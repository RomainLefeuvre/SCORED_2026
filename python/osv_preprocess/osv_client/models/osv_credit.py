from typing import Any, Dict, List, Type, TypeVar, Union, cast

from attrs import define, field

from ..models.osv_credit_type import OsvCreditType
from ..types import UNSET, Unset

T = TypeVar("T", bound="OsvCredit")


@define
class OsvCredit:
    """
    Attributes:
        name (Union[Unset, str]): The name to give credit to.
        contact (Union[Unset, List[str]]): Contact methods (URLs).
        type (Union[Unset, OsvCreditType]):  Default: OsvCreditType.UNSPECIFIED.
    """

    name: Union[Unset, str] = UNSET
    contact: Union[Unset, List[str]] = UNSET
    type: Union[Unset, OsvCreditType] = OsvCreditType.UNSPECIFIED
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        name = self.name
        contact: Union[Unset, List[str]] = UNSET
        if not isinstance(self.contact, Unset):
            contact = self.contact

        type: Union[Unset, str] = UNSET
        if not isinstance(self.type, Unset):
            type = self.type.value

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if name is not UNSET:
            field_dict["name"] = name
        if contact is not UNSET:
            field_dict["contact"] = contact
        if type is not UNSET:
            field_dict["type"] = type

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        d = src_dict.copy()
        name = d.pop("name", UNSET)

        contact = cast(List[str], d.pop("contact", UNSET))

        _type = d.pop("type", UNSET)
        type: Union[Unset, OsvCreditType]
        if isinstance(_type, Unset):
            type = UNSET
        else:
            type = OsvCreditType(_type)

        osv_credit = cls(
            name=name,
            contact=contact,
            type=type,
        )

        osv_credit.additional_properties = d
        return osv_credit

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
