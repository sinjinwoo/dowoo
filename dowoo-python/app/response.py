from typing import Any, Optional


def success_envelope(status: int, data: Any, message: str) -> dict:
    return {"status": status, "data": data, "message": message, "error": None}


def error_envelope(status: int, code: str, message: str, details: Optional[str] = None) -> dict:
    return {"status": status, "data": None, "message": message, "error": {"code": code, "details": details}}
