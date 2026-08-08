import hmac
import json
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, status
from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool

from app.config import settings
from app.models import BatchRequest, BatchResponse, EventAcknowledgement


INSERT_SQL = """
INSERT INTO notifications (
    event_id, device_id, device_name, package_name, app_name, notification_key,
    notification_id, notification_tag, title, body, expanded_text, subtext,
    category, channel_id, group_key, posted_at, captured_at, removed_at,
    event_type, raw_metadata, payload_version
) VALUES (
    %(event_id)s, %(device_id)s, %(device_name)s, %(package_name)s, %(app_name)s,
    %(notification_key)s, %(notification_id)s, %(notification_tag)s, %(title)s,
    %(body)s, %(expanded_text)s, %(subtext)s, %(category)s, %(channel_id)s,
    %(group_key)s, %(posted_at)s, %(captured_at)s, %(removed_at)s, %(event_type)s,
    %(raw_metadata)s::jsonb, %(payload_version)s
)
ON CONFLICT (event_id) DO NOTHING
RETURNING event_id
"""

rate_windows: dict[str, deque[float]] = defaultdict(deque)


@asynccontextmanager
async def lifespan(app: FastAPI):
    pool = AsyncConnectionPool(settings.database_url, min_size=1, max_size=10, open=False)
    await pool.open()
    async with pool.connection() as conn:
        await conn.execute(Path(__file__).with_name("schema.sql").read_text())
    app.state.pool = pool
    yield
    await pool.close()


app = FastAPI(title="Notification Archive API", version="1.0.0", lifespan=lifespan)


async def require_token(authorization: Annotated[str | None, Header()] = None) -> None:
    prefix = "Bearer "
    if not authorization or not authorization.startswith(prefix):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing bearer token")
    if not hmac.compare_digest(authorization[len(prefix):], settings.api_token):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid bearer token")


async def enforce_limits(request: Request) -> None:
    length = request.headers.get("content-length")
    if length and int(length) > settings.max_request_bytes:
        raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, "Request too large")
    key = request.client.host if request.client else "unknown"
    now = time.monotonic()
    window = rate_windows[key]
    while window and window[0] < now - 60:
        window.popleft()
    if len(window) >= settings.rate_limit_per_minute:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Rate limit exceeded")
    window.append(now)


@app.get("/api/v1/health")
async def health(request: Request):
    async with request.app.state.pool.connection() as conn:
        await conn.execute("SELECT 1")
    return {"status": "ok"}


@app.post(
    "/api/v1/notifications/batch",
    response_model=BatchResponse,
    dependencies=[Depends(require_token), Depends(enforce_limits)],
)
async def ingest_batch(payload: BatchRequest, request: Request) -> BatchResponse:
    acknowledgements = []
    async with request.app.state.pool.connection() as conn:
        async with conn.transaction():
            for event in payload.events:
                values = event.model_dump()
                values["raw_metadata"] = json.dumps(values["raw_metadata"], ensure_ascii=False)
                cursor = await conn.execute(INSERT_SQL, values)
                inserted = await cursor.fetchone()
                acknowledgements.append(
                    EventAcknowledgement(
                        event_id=event.event_id,
                        accepted=True,
                        duplicate=inserted is None,
                    )
                )
    return BatchResponse(acknowledgements=acknowledgements)


@app.get("/api/v1/notifications", dependencies=[Depends(require_token), Depends(enforce_limits)])
async def list_notifications(
    request: Request,
    q: Annotated[str | None, Query(max_length=500)] = None,
    package_name: str | None = None,
    device_id: str | None = None,
    event_type: str | None = None,
    since: str | None = None,
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
):
    clauses = []
    params: dict[str, object] = {"limit": limit}
    if q:
        clauses.append("to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(body, '') || ' ' || coalesce(expanded_text, '')) @@ plainto_tsquery('simple', %(q)s)")
        params["q"] = q
    for name, value in (("package_name", package_name), ("device_id", device_id), ("event_type", event_type)):
        if value:
            clauses.append(f"{name} = %({name})s")
            params[name] = value
    if since:
        clauses.append("captured_at >= %(since)s::timestamptz")
        params["since"] = since
    where = " WHERE " + " AND ".join(clauses) if clauses else ""
    sql = "SELECT * FROM notifications" + where + " ORDER BY captured_at DESC, id DESC LIMIT %(limit)s"
    async with request.app.state.pool.connection() as conn:
        cursor = conn.cursor(row_factory=dict_row)
        await cursor.execute(sql, params)
        rows = await cursor.fetchall()
    return rows


@app.get("/api/v1/sync/status", dependencies=[Depends(require_token)])
async def sync_status(request: Request):
    sql = "SELECT count(*) AS total, max(received_at) AS last_received_at, count(DISTINCT device_id) AS devices FROM notifications"
    async with request.app.state.pool.connection() as conn:
        cursor = conn.cursor(row_factory=dict_row)
        await cursor.execute(sql)
        return await cursor.fetchone()
