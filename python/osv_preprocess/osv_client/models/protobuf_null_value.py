from enum import Enum


class ProtobufNullValue(str, Enum):
    NULL_VALUE = "NULL_VALUE"

    def __str__(self) -> str:
        return str(self.value)
