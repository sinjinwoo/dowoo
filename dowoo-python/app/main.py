from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.crawl.router import router as crawl_router
from app.exceptions import CrawlError
from app.response import error_envelope
from app.translate.router import router as translate_router

app = FastAPI(title="dowoo AI API")

app.include_router(crawl_router)
app.include_router(translate_router)


@app.get("/health")
def health():
    return {"status": "UP"}


@app.exception_handler(CrawlError)
async def handle_crawl_error(request: Request, exc: CrawlError):
    return JSONResponse(status_code=exc.status, content=error_envelope(exc.status, exc.code, exc.message))


@app.exception_handler(StarletteHTTPException)
async def handle_http_exception(request: Request, exc: StarletteHTTPException):
    detail = exc.detail
    if isinstance(detail, dict):
        code = detail.get("code", "ERROR")
        message = detail.get("message", str(detail))
    else:
        code = "ERROR"
        message = str(detail)
    return JSONResponse(status_code=exc.status_code, content=error_envelope(exc.status_code, code, message))
