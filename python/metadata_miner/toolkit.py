import git

from osv_preprocess.osv_range import EventType, OsvRange


def get_repo_owner_name_pair(repo_url):
    raw = repo_url.replace("https://","").replace("git@", "").replace(".git", "")
    repo_name = raw.split("/")[-1]
    repo_owner = raw.split("/")[-2]
    return (repo_owner,repo_name)


def commit_exists( repo_path: str, commit_hash: str) -> bool:
        """
        Check if a given commit hash exists in the Git repository.

        :param repo_path: 
        :param commit_hash: The commit hash to check
        :return: True if the commit exists, False otherwise
        """
        try:
            repo = git.Repo(repo_path)
            repo.commit(commit_hash)
            return True
        except ( Exception):
            return False
        
def get_fixed(range:OsvRange)-> set[str]:
    return set([id for id,event  in range.events.items() if event == EventType.FIXED])
