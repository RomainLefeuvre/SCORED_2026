from typing import TYPE_CHECKING, Any, Dict, List, Type, TypeVar, Union, cast

from attrs import define, field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.osv_affected_database_specific import OsvAffectedDatabaseSpecific
    from ..models.osv_affected_ecosystem_specific import OsvAffectedEcosystemSpecific
    from ..models.osv_package import OsvPackage
    from ..models.osv_range import OsvRange
    from ..models.osv_severity import OsvSeverity


T = TypeVar("T", bound="OsvAffected")


@define
class OsvAffected:
    """Affected versions and commits.

    Attributes:
        package (Union[Unset, OsvPackage]): Package information and version.
        ranges (Union[Unset, List['OsvRange']]): Required. Range information.
        versions (Union[Unset, List[str]]): Optional. List of affected versions.
        ecosystem_specific (Union[Unset, OsvAffectedEcosystemSpecific]): Optional. JSON object holding additional
            information about the
            vulnerability as defined by the ecosystem for which the record applies.
        database_specific (Union[Unset, OsvAffectedDatabaseSpecific]): Optional. JSON object holding additional
            information about the
            vulnerability as defined by the database for which the record applies.
        severity (Union[Unset, List['OsvSeverity']]): Optional. Severity of the vulnerability for this package.
    """

    package: Union[Unset, "OsvPackage"] = UNSET
    ranges: Union[Unset, List["OsvRange"]] = UNSET
    versions: Union[Unset, List[str]] = UNSET
    ecosystem_specific: Union[Unset, "OsvAffectedEcosystemSpecific"] = UNSET
    database_specific: Union[Unset, "OsvAffectedDatabaseSpecific"] = UNSET
    severity: Union[Unset, List["OsvSeverity"]] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        package: Union[Unset, Dict[str, Any]] = UNSET
        if not isinstance(self.package, Unset):
            package = self.package.to_dict()

        ranges: Union[Unset, List[Dict[str, Any]]] = UNSET
        if not isinstance(self.ranges, Unset):
            ranges = []
            for ranges_item_data in self.ranges:
                ranges_item = ranges_item_data.to_dict()

                ranges.append(ranges_item)

        versions: Union[Unset, List[str]] = UNSET
        if not isinstance(self.versions, Unset):
            versions = self.versions

        ecosystem_specific: Union[Unset, Dict[str, Any]] = UNSET
        if not isinstance(self.ecosystem_specific, Unset):
            ecosystem_specific = self.ecosystem_specific.to_dict()

        database_specific: Union[Unset, Dict[str, Any]] = UNSET
        if not isinstance(self.database_specific, Unset):
            database_specific = self.database_specific.to_dict()

        severity: Union[Unset, List[Dict[str, Any]]] = UNSET
        if not isinstance(self.severity, Unset):
            severity = []
            for severity_item_data in self.severity:
                severity_item = severity_item_data.to_dict()

                severity.append(severity_item)

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if package is not UNSET:
            field_dict["package"] = package
        if ranges is not UNSET:
            field_dict["ranges"] = ranges
        if versions is not UNSET:
            field_dict["versions"] = versions
        if ecosystem_specific is not UNSET:
            field_dict["ecosystemSpecific"] = ecosystem_specific
        if database_specific is not UNSET:
            field_dict["databaseSpecific"] = database_specific
        if severity is not UNSET:
            field_dict["severity"] = severity

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        from ..models.osv_affected_database_specific import OsvAffectedDatabaseSpecific
        from ..models.osv_affected_ecosystem_specific import OsvAffectedEcosystemSpecific
        from ..models.osv_package import OsvPackage
        from ..models.osv_range import OsvRange
        from ..models.osv_severity import OsvSeverity

        d = src_dict.copy()
        _package = d.pop("package", UNSET)
        package: Union[Unset, OsvPackage]
        if isinstance(_package, Unset):
            package = UNSET
        else:
            package = OsvPackage.from_dict(_package)

        ranges = []
        _ranges = d.pop("ranges", UNSET)
        for ranges_item_data in _ranges or []:
            ranges_item = OsvRange.from_dict(ranges_item_data)

            ranges.append(ranges_item)

        versions = cast(List[str], d.pop("versions", UNSET))

        _ecosystem_specific = d.pop("ecosystemSpecific", UNSET)
        ecosystem_specific: Union[Unset, OsvAffectedEcosystemSpecific]
        if isinstance(_ecosystem_specific, Unset):
            ecosystem_specific = UNSET
        else:
            ecosystem_specific = OsvAffectedEcosystemSpecific.from_dict(_ecosystem_specific)

        _database_specific = d.pop("databaseSpecific", UNSET)
        database_specific: Union[Unset, OsvAffectedDatabaseSpecific]
        if isinstance(_database_specific, Unset):
            database_specific = UNSET
        else:
            database_specific = OsvAffectedDatabaseSpecific.from_dict(_database_specific)

        severity = []
        _severity = d.pop("severity", UNSET)
        for severity_item_data in _severity or []:
            severity_item = OsvSeverity.from_dict(severity_item_data)

            severity.append(severity_item)

        osv_affected = cls(
            package=package,
            ranges=ranges,
            versions=versions,
            ecosystem_specific=ecosystem_specific,
            database_specific=database_specific,
            severity=severity,
        )

        osv_affected.additional_properties = d
        return osv_affected

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
