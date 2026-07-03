import json

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.auth import require_internal_token
from app.schemas import TranslateRequest
from app.translate.gemini_client import translate_stream

router = APIRouter(prefix="/internal", tags=["translate"])


def _format_sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/translate/stream", dependencies=[Depends(require_internal_token)])
async def translate(request: TranslateRequest):
    async def event_source():
        async for item in translate_stream(
            api_keys=request.apiKeys,
            model=request.model,
            system_prompt=request.systemPrompt,
            translation_note=request.translationNote or "",
            original_text=request.originalText,
            thinking_budget=request.thinkingBudget,
        ):
            yield _format_sse(item["event"], item["data"])

    return StreamingResponse(event_source(), media_type="text/event-stream")
