import json, time, logging
from urllib import request, error

from metadata_miner.fork_metadata import ForkMetadata

class ArchivedMetadata(ForkMetadata):
    def __init__(self, fork,workspace,token="YOUR TOKEN"):
        self.token = token
        super().__init__(fork=fork, workspace=workspace,clone=False, log_dir="log")

    def computeMetadata(self):
        url = self.fork_remote_url.replace("github.com", "api.github.com/repos").removesuffix(".git")

        headers = {
            "User-Agent": "ArchivedMetadataChecker",
            "Authorization": f"Bearer {self.token}" if self.token else None
        }

        req = request.Request(url, headers={k: v for k, v in headers.items() if v})

        for attempt in range(5):
            try:
                with request.urlopen(req) as resp:
                    headers = resp.headers
                    if int(headers.get("X-RateLimit-Remaining", 1)) == 0:
                        wait = max(int(headers.get("X-RateLimit-Reset", 0)) - int(time.time()), 1)
                        time.sleep(wait)
                        continue
                    data = json.load(resp)
                    archived = data.get("archived", False)
                    logging.info(f"{self.fork_remote_url} : {'archived' if archived else 'not archived'}")
                    return not archived

            except error.HTTPError as e:
                if e.code in (403, 429) and 'X-RateLimit-Reset' in e.headers:
                    wait = max(int(e.headers["X-RateLimit-Reset"]) - int(time.time()), 1)
                    logging.info(f"Rate limit exceeded. Waiting for {wait} seconds.")
                    time.sleep(wait)
                else:
                    logging.info(f"Repo deleted {self.fork_remote_url}: {e}")

            except error.URLError:
                time.sleep(2 ** attempt)

            except Exception as e:
                logging.error(f"Error occurred: {e}")
                continue

        raise Exception(f"Failed to fetch metadata for {self.fork_remote_url} after multiple attempts.")
