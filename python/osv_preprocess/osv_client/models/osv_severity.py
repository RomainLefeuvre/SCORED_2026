from typing import Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..models.osv_severity_type import OsvSeverityType
from ..types import UNSET, Unset

T = TypeVar("T", bound="OsvSeverity")


@define
class OsvSeverity:
    """
    Attributes:
        type (Union[Unset, OsvSeverityType]): Type of the severity. Default: OsvSeverityType.UNSPECIFIED.
        score (Union[Unset, str]): The quantitative score.
    """

    type: Union[Unset, OsvSeverityType] = OsvSeverityType.UNSPECIFIED
    score: Union[Unset, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        type: Union[Unset, str] = UNSET
        if not isinstance(self.type, Unset):
            type = self.type.value

        score = self.score

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if type is not UNSET:
            field_dict["type"] = type
        if score is not UNSET:
            field_dict["score"] = score

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        d = src_dict.copy()
        _type = d.pop("type", UNSET)
        type: Union[Unset, OsvSeverityType]
        if isinstance(_type, Unset):
            type = UNSET
        else:
            type = OsvSeverityType(_type)

        score = d.pop("score", UNSET)

        osv_severity = cls(
            type=type,
            score=score,
        )

        osv_severity.additional_properties = d
        return osv_severity

    @property
    def additional_keys(self) -> List[str]:
        return list(self.additional_properties.keys())

    def __getitem__(self, key: str) -> Any:
        return self.additional_properties[key]

    def __setitem__(self, key: str, value: Any) -> None:
        self.additional_properties[key] = value

    def __delitem__(self, key: str) -> None:
        del self.additional_properties[key]

    def __contains__(self, key: str) -> bool:
        return key in self.additional_properties
