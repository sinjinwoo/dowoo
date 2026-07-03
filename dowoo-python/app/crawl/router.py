from fastapi import APIRouter, Depends

from app.auth import require_internal_token
from app.crawl.registry import crawl_chapter
from app.response import success_envelope
from app.schemas import CrawlRequest

router = APIRouter(prefix="/internal", tags=["crawl"])


@router.post("/crawl", dependencies=[Depends(require_internal_token)])
async def crawl(request: CrawlRequest):
    data = await crawl_chapter(request.url)
    return success_envelope(200, data, "크롤링 성공")
