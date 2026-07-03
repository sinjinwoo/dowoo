from typing import List, Optional

from pydantic import BaseModel


class CrawlRequest(BaseModel):
    url: str


class TranslateRequest(BaseModel):
    apiKeys: List[str]
    model: str
    thinkingBudget: Optional[int] = None
    systemPrompt: str
    translationNote: Optional[str] = ""
    originalText: str
