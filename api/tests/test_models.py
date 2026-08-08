from datetime import UTC, datetime
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.models import BatchRequest, NotificationEvent


def event():
    return NotificationEvent(
        event_id=uuid4(),
        device_id="phone",
        package_name="org.example",
        captured_at=datetime.now(UTC),
        event_type="posted",
    )


def test_batch_accepts_valid_event():
    assert len(BatchRequest(events=[event()]).events) == 1


def test_batch_rejects_more_than_100_events():
    with pytest.raises(ValidationError):
        BatchRequest(events=[event() for _ in range(101)])

