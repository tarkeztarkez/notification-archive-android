from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str
    api_token: str = Field(min_length=32)
    max_request_bytes: int = 1_048_576
    rate_limit_per_minute: int = 120
    log_level: str = "INFO"


settings = Settings()

