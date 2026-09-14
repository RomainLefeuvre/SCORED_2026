from typing import Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

T = TypeVar("T", bound="OsvEvent")


@define
class OsvEvent:
    """Version events.

    Attributes:
        introduced (Union[Unset, str]): The earliest version/commit where this vulnerability
            was introduced in.
        fixed (Union[Unset, str]): The version/commit that this vulnerability was fixed in.
        limit (Union[Unset, str]): The limit to apply to the range.
        last_affected (Union[Unset, str]): The last affected version.
    """

    introduced: Union[Unset, str] = UNSET
    fixed: Union[Unset, str] = UNSET
    limit: Union[Unset, str] = UNSET
    last_affected: Union[Unset, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        introduced = self.introduced
        fixed = self.fixed
        limit = self.limit
        last_affected = self.last_affected

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if introduced is not UNSET:
            field_dict["introduced"] = introduced
        if fixed is not UNSET:
            field_dict["fixed"] = fixed
        if limit is not UNSET:
            field_dict["limit"] = limit
        if last_affected is not UNSET:
            field_dict["last_affected"] = last_affected

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        d = src_dict.copy()
        introduced = d.pop("introduced", UNSET)

        fixed = d.pop("fixed", UNSET)

        limit = d.pop("limit", UNSET)

        last_affected = d.pop("last_affected", UNSET)

        osv_event = cls(
            introduced=introduced,
            fixed=fixed,
            limit=limit,
            last_affected=last_affected,
        )

        osv_event.additional_properties = d
        return osv_event

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
    
    
