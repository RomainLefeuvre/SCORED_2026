from typing import Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..models.osv_reference_type import OsvReferenceType
from ..types import UNSET, Unset

T = TypeVar("T", bound="OsvReference")


@define
class OsvReference:
    """Reference URL.

    Attributes:
        type (Union[Unset, OsvReferenceType]):  Default: OsvReferenceType.NONE.
        url (Union[Unset, str]): Required. The URL.
    """

    type: Union[Unset, OsvReferenceType] = OsvReferenceType.NONE
    url: Union[Unset, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        type: Union[Unset, str] = UNSET
        if not isinstance(self.type, Unset):
            type = self.type.value

        url = self.url

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if type is not UNSET:
            field_dict["type"] = type
        if url is not UNSET:
            field_dict["url"] = url

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        d = src_dict.copy()
        _type = d.pop("type", UNSET)
        type: Union[Unset, OsvReferenceType]
        if isinstance(_type, Unset):
            type = UNSET
        else:
            type = OsvReferenceType(_type)

        url = d.pop("url", UNSET)

        osv_reference = cls(
            type=type,
            url=url,
        )

        osv_reference.additional_properties = d
        return osv_reference

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
