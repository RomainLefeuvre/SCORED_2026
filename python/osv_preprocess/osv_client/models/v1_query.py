from typing import TYPE_CHECKING, Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.osv_package import OsvPackage


T = TypeVar("T", bound="V1Query")


@define
class V1Query:
    """Query format.

    Attributes:
        commit (Union[Unset, str]): The commit hash to query for. If specified, `version` should not be set.
        version (Union[Unset, str]): The version string to query for. A fuzzy match is done against upstream
            versions. If specified, `commit` should not be set.
        package (Union[Unset, OsvPackage]): Package information and version.
        page_token (Union[Unset, str]):
    """

    commit: Union[Unset, str] = UNSET
    version: Union[Unset, str] = UNSET
    package: Union[Unset, "OsvPackage"] = UNSET
    page_token: Union[Unset, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        commit = self.commit
        version = self.version
        package: Union[Unset, Dict[str, Any]] = UNSET
        if not isinstance(self.package, Unset):
            package = self.package.to_dict()

        page_token = self.page_token

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if commit is not UNSET:
            field_dict["commit"] = commit
        if version is not UNSET:
            field_dict["version"] = version
        if package is not UNSET:
            field_dict["package"] = package
        if page_token is not UNSET:
            field_dict["pageToken"] = page_token

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        from ..models.osv_package import OsvPackage

        d = src_dict.copy()
        commit = d.pop("commit", UNSET)

        version = d.pop("version", UNSET)

        _package = d.pop("package", UNSET)
        package: Union[Unset, OsvPackage]
        if isinstance(_package, Unset):
            package = UNSET
        else:
            package = OsvPackage.from_dict(_package)

        page_token = d.pop("pageToken", UNSET)

        v1_query = cls(
            commit=commit,
            version=version,
            package=package,
            page_token=page_token,
        )

        v1_query.additional_properties = d
        return v1_query

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
