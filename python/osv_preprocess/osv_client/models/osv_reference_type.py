from enum import Enum


class OsvReferenceType(str, Enum):
    ADVISORY = "ADVISORY"
    ARTICLE = "ARTICLE"
    EVIDENCE = "EVIDENCE"
    FIX = "FIX"
    NONE = "NONE"
    PACKAGE = "PACKAGE"
    REPORT = "REPORT"
    WEB = "WEB"

    def __str__(self) -> str:
        return str(self.value)
