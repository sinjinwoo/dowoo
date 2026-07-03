from typing import Optional

from fastapi import Header, HTTPException

from app.config import INTERNAL_TOKEN


async def require_internal_token(x_internal_token: Optional[str] = Header(default=None)):
    if x_internal_token != INTERNAL_TOKEN:
        raise HTTPException(
            status_code=401,
            detail={"code": "INTERNAL_UNAUTHORIZED", "message": "X-Internal-Token이 올바르지 않습니다."},
        )
