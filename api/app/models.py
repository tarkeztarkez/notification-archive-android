from datetime import datetime
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class NotificationEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")

    event_id: UUID
    device_id: str = Field(min_length=1, max_length=128)
    device_name: str | None = Field(default=None, max_length=128)
    package_name: str = Field(min_length=1, max_length=255)
    app_name: str | None = Field(default=None, max_length=255)
    notification_key: str | None = Field(default=None, max_length=1024)
    notification_id: int | None = None
    notification_tag: str | None = Field(default=None, max_length=512)
    title: str | None = None
    body: str | None = None
    expanded_text: str | None = None
    subtext: str | None = None
    category: str | None = Field(default=None, max_length=128)
    channel_id: str | None = Field(default=None, max_length=255)
    group_key: str | None = Field(default=None, max_length=512)
    posted_at: datetime | None = None
    captured_at: datetime
    removed_at: datetime | None = None
    event_type: Literal["posted", "updated", "removed"]
    raw_metadata: dict[str, Any] = Field(default_factory=dict)
    payload_version: int = Field(default=1, ge=1, le=100)


class BatchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    events: list[NotificationEvent] = Field(min_length=1, max_length=100)


class EventAcknowledgement(BaseModel):
    event_id: UUID
    accepted: bool
    duplicate: bool = False
    error: str | None = None


class BatchResponse(BaseModel):
    acknowledgements: list[EventAcknowledgement]

