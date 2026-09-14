from http import HTTPStatus
from typing import Any, Dict, Optional, Union

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.v1_batch_query import V1BatchQuery
from ...models.v1_batch_vulnerability_list import V1BatchVulnerabilityList
from ...types import Response


def _get_kwargs(
    *,
    json_body: V1BatchQuery,
) -> Dict[str, Any]:

    pass

    json_json_body = json_body.to_dict()

    return {
        "method": "post",
        "url": "/v1/querybatch",
        "json": json_json_body,
    }


def _parse_response(
    *, client: Union[AuthenticatedClient, Client], response: httpx.Response
) -> Optional[V1BatchVulnerabilityList]:
    if response.status_code == HTTPStatus.OK:
        response_200 = V1BatchVulnerabilityList.from_dict(response.json())

        return response_200
    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(
    *, client: Union[AuthenticatedClient, Client], response: httpx.Response
) -> Response[V1BatchVulnerabilityList]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: Union[AuthenticatedClient, Client],
    json_body: V1BatchQuery,
) -> Response[V1BatchVulnerabilityList]:
    """Query vulnerabilities (batched) for given package versions and commits.
    This currently allows a maximum of 1000 package versions to be included in a single query.

    Args:
        json_body (V1BatchQuery): Batch query format.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[V1BatchVulnerabilityList]
    """

    kwargs = _get_kwargs(
        json_body=json_body,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    *,
    client: Union[AuthenticatedClient, Client],
    json_body: V1BatchQuery,
) -> Optional[V1BatchVulnerabilityList]:
    """Query vulnerabilities (batched) for given package versions and commits.
    This currently allows a maximum of 1000 package versions to be included in a single query.

    Args:
        json_body (V1BatchQuery): Batch query format.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        V1BatchVulnerabilityList
    """

    return sync_detailed(
        client=client,
        json_body=json_body,
    ).parsed


async def asyncio_detailed(
    *,
    client: Union[AuthenticatedClient, Client],
    json_body: V1BatchQuery,
) -> Response[V1BatchVulnerabilityList]:
    """Query vulnerabilities (batched) for given package versions and commits.
    This currently allows a maximum of 1000 package versions to be included in a single query.

    Args:
        json_body (V1BatchQuery): Batch query format.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[V1BatchVulnerabilityList]
    """

    kwargs = _get_kwargs(
        json_body=json_body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: Union[AuthenticatedClient, Client],
    json_body: V1BatchQuery,
) -> Optional[V1BatchVulnerabilityList]:
    """Query vulnerabilities (batched) for given package versions and commits.
    This currently allows a maximum of 1000 package versions to be included in a single query.

    Args:
        json_body (V1BatchQuery): Batch query format.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        V1BatchVulnerabilityList
    """

    return (
        await asyncio_detailed(
            client=client,
            json_body=json_body,
        )
    ).parsed
