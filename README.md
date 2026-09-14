
<a name="readme-top"></a>




<!-- PROJECT LOGO -->
<br />
<div align="center">


<h3 align="center">Did You Forkget It? Detecting One-Day Vulnerabilities in Open-source Forks
With Global History Analysis</h3>

  <p align="center">
    Companion repository of SCORED Submission
      </p>
</div>



The reproduction package is composed of multiple tasks that are orchestrated using luigi scheduler [Luigi Scheduler](https://luigi.readthedocs.io/en/stable/central_scheduler.html). For each research question we will detail the associated tasks.

Note: Due to space constraints and the anonymous GitHub submission, this artifact does not include all intermediate data. The final version will include the complete data. Additionally, the anonymization process may introduce compilation errors.


##  TRACKING VULNERABLE COMMITS ACROSS FORKS

### Preprocessing and Commit Labelling

The labelling of the software heritage graph consists in applying the labelling algorithm presented on the paper (III.B), and available as pseudocode `algorithm.pdf`.

**Input data :** 
* OSV report 
* Software Heritage Compressed Graph

**Output :**
* Vulnerability label : Commit --> Vulnerability


*Luigi workflow Tasks*:
   - `DownloadOsvTask` : Download the OSV database
   - `ComputeVulnRangeTask` : Pre-process Range 
   - `OsvLabelTask` : Label the graph 
   - `PackageJarTask` : Package the different task
`

### ANALYSIS

#### Analysing the data

All the graph presented on the paper are produced with the jupyter notebook : [`python/TRACKING_SECTION_3_ANALYSIS.ipynb`](python/analysis.ipynb)

#### Luigi task needed to precompute data
*Luigi workflow Tasks*:
  - `RangeAnalyzerTask`
  - `VulnerableRepoAnalysisTask`
  - `VulnerableRevisionAnalysisTask`
  - `VulnerableRevisionPerUrlAnalysisTask`
  - `VulnerableUnpatchedAnalysisTask`

*Secondary luigi workflow tasks* : 
   - `TopoSortTask`  : Compute the topological order of the graph node for `CommitToOriginTask`
   - `CommitToOriginTask` : Perform a mapping between commits and origins
   - `ProvenanceComputeTask`: Extract the provenance for vulnerable commits, map vulnerable commits with associated origin



## RQ2- TRACKING ONE DAY VULNERABILITY

### Filtering high-impact forks and vulnerability
The filtering of high impact forks and vulnerability is performed in this notebook :

[`python/RQ_SECTION_4_ANALYSIS.ipynb`](python/RQ_SECTION_4_ANALYSIS.ipynb)


### Manual vetting

The notebook related to manual vetting is located :
[`python/vetting_analysis.ipynb`](python/vetting_analysis.ipynb)

The artifacts of the manual analysis are available in a spreadsheet `RQ_SECTION_4_VETTING_ANALYSIS/classification.ods`

It contains two sheets:
`Data quality`: Manual classification of the ranges, i.e. whether they had valid FIXED commits.
`Vetting`: The manual vetting of each vulnerability, following the process described in the paper.

# Dependency analysis tooling 

The usage of the tools is based on a Postgres database, that must be created from the output of the initial analysis.

Launch the docker Postgres database:
```sh
cd java/fr.inria.diverse.swh_osv/src/main/java/fr/inria/diverse/database
docker compose up
```

Then, run the JavaScript `/PostgresCreate.java`



### Go

The analysis on go repositories can be launched via the script `python/dependency_tool/go_analysis/go_analysis.py`, the amount can be set in the bottom of the file ` search_top_go_repos(NUMBER_OF_REPOSITORY,...`. Using a github token is advised, and can be set up within the script as well `GITHUB_TOKEN = `

#### Submodule analysis

The submodule tool can be run via the following command:
`python ./submodule_tool.py ./example_vulnerable_repository/`

It will output a list of potential vulnerabilities in the submodules of `example_vulnerable_repository`

Note: this script needs the docker Postgres database to be launched.

## Data Folder - Overview

```
.
├── algorithm.pdf
├── data
│   ├── google_sheet.ini
│   ├── TRACKING_SECTION_3_ANALYSIS
│   │   ├── not_resolved_head.json       
│   │   └── reposMetadata_final.json  
│   ├── RQ_SECTION_4_ANALYSIS                    //Metadata store and intermediate filtering artefact
│   │   ├── metadata_store           
│   │   │   ├── archived.jsonl
│   │   │   ├── cherry_pick.jsonl
│   │   │   ├── cve_id_in_history.jsonl
│   │   │   ├── divergence.jsonl
│   │   │   ├── main_branch
│   │   │   └── swhid_to_branch.json
│   │   ├── Metric5_archived.jsonl.zst
│   │   ├── Metric5cherry_pick.jsonl.zst
│   │   ├── Metric5_criticity.jsonl.zst
│   │   ├── Metric5cve_id_in_history.jsonl.zst
│   │   ├── Metric5data_quality.jsonl.zst
│   │   ├── Metric5divergence.jsonl.zst
│   │   ├── Metric5_github.jsonl.zst
│   │   ├── Metric5.jsonl.zst
│   │   ├── Metric5_main_branch.jsonl.zst
│   │   ├── Metric5_popularity.jsonl.zst
│   │   ├── Metric5sibling.jsonl.zst
│   │   ├── Metric5_timestamp.jsonl.zst
│   │   └── origin-metadata.csv.zst 
│   └── RQ_SECTION_4_VETTING_ANALYSIS
│       ├── data_quality.csv
│       ├── macro
│       │   ├── latex_author_eval_macro.tex
│       │   ├── latex_author_eval_without_outlier_macro.tex
│       │   ├── latex_macros_fork_distrib.tex
│       │   └── latex_response_macro.tex
│       └── vetting_result.csv

```


## How to reproduce the experiment 
### Installation 

1. Open a terminal in the python folder
2. Create a virtual env   
```python -m venv ./.venv```
3. Activate your environment  
```source .venv/bin/activate```
2. Install dependencies  
```pip install -r ./requirements.txt```

### Running the Luigi workflow

This first step is to get the graph wanted for the analysis, available for download via Amazon s3 https://docs.softwareheritage.org/devel/swh-export/graph/dataset.html.

The next necessary steps are to `cd` in the luigi directory, configure the path to the graph in the config file.
The amount of RAM must also be set with the environment variable SWHOSV_RAM(`export SWHOSV_RAM=500G`)

for example
```cfg
graphPath =XXX/graph/2021-03-23-popular-3k-python/compressed/graph
```

and launch the luigid server with the command `luigid`

### Launch a Task
Then any of the following tasks can be launched with the Luigi CLI:
 - DownloadOsvTask
 - ComputeVulnRangeTask
 - TopoSortTask
 - PackageJarTask
 - CommitToOriginTask
 - OsvLabelTask
 - ProvenanceComputeTask
 - RangeAnalyzerTask
 - VulnerableRepoAnalysisTask
 - VulnerableRevisionAnalysisTask
 - VulnerableRevisionPerUrlAnalysisTask
 - VulnerableUnpatchedAnalysisTask

Preferably you would use the previously created virtual environment, launch the lugi server and launch the python file `luigiWorkflow`
1. cd in the luigi directory<br>
```cd python/luigi```
2. Launch the Server<br>
```luigid```
3. In parallel, launch the full analysis<br>
```python3 luigiWorkflow.py```


this will run all the final tasks(VulnerableRepoAnalysisTask, VulnerableRevisionAnalysisTask, VulnerableRevisionPerUrlAnalysisTask, VulnerableUnpatchedAnalysisTask)

Otherwise you can run a specific task with this command
by modifying the last lines of the `luigiWorkflow.py` file.


This will create a folder `outputs/` containing all the data of all the substeps, with the most important ones being:

- `python/luigi/outputs/VulnerableRepoAnalysis`: maps ranges to lists of new vulnerable origins(i.e. forks).
- `python/luigi/outputs/VulnerableRevisionAnalysis`: maps ranges to lists of new vulnerable revisions(i.e. commits).  
- `python/luigi/outputs/VulnerableRevisionPerUrlAnalysis`: "Merge" of the two previous analysis, to get which revision belongs to which new origin.
- `python/luigi/outputs/VulnerableUnpatchedAnalysis`: Maps ranges to lists of "unpatched heads", i.e. commits that are vulnerable and do not possess any children.







# SCORED_2026
