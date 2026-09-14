from typing import Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

T = TypeVar("T", bound="OsvPackage")


@define
class OsvPackage:
    """Package information and version.

    Attributes:
        name (Union[Unset, str]): Required. Name of the package. Should match the name used in the package
            ecosystem (e.g. the npm package name). For C/C++ projects integrated in
            OSS-Fuzz, this is the name used for the integration.
        ecosystem (Union[Unset, str]): Required. The ecosystem for this package.
            For the complete list of valid ecosystem names, see
            <https://ossf.github.io/osv-schema/#affectedpackage-field>.
        purl (Union[Unset, str]): Optional. The package URL for this package.
    """

    name: Union[Unset, str] = UNSET
    ecosystem: Union[Unset, str] = UNSET
    purl: Union[Unset, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        name = self.name
        ecosystem = self.ecosystem
        purl = self.purl

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if name is not UNSET:
            field_dict["name"] = name
        if ecosystem is not UNSET:
            field_dict["ecosystem"] = ecosystem
        if purl is not UNSET:
            field_dict["purl"] = purl

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        d = src_dict.copy()
        name = d.pop("name", UNSET)

        ecosystem = d.pop("ecosystem", UNSET)

        purl = d.pop("purl", UNSET)

        osv_package = cls(
            name=name,
            ecosystem=ecosystem,
            purl=purl,
        )

        osv_package.additional_properties = d
        return osv_package

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
