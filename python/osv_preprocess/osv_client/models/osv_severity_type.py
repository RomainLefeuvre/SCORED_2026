from enum import Enum


class OsvSeverityType(str, Enum):
    CVSS_V2 = "CVSS_V2"
    CVSS_V3 = "CVSS_V3"
    CVSS_V4 = "CVSS_V4"
    UNSPECIFIED = "UNSPECIFIED"

    def __str__(self) -> str:
        return str(self.value)
