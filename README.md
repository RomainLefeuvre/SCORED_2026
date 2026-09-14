
<a name="readme-top"></a>




<!-- PROJECT LOGO -->
<br />
<div align="center">


<h3 align="center">Did You Forkget It? Detecting One-Day Vulnerabilities in Open-source Forks
With Global History Analysis</h3>

  <p align="center">
    Companion repository / reproduction package for the paper accepted at <a href="https://doi.org/10.1145/3848003.3848009">SCORED '26</a>
      </p>
</div>

**Authors:** Romain Lefeuvre<sup>1</sup>, Charly Reux<sup>1</sup>, Stefano Zacchiroli<sup>2</sup>, Olivier Barais<sup>1</sup>, Benoit Combemale<sup>3</sup>

<sup>1</sup> University of Rennes, Rennes, France
<sup>2</sup> LTCI, Télécom Paris, Institut Polytechnique de Paris, Palaiseau, France
<sup>3</sup> Inria, Rennes, France

> Romain Lefeuvre, Charly Reux, Stefano Zacchiroli, Olivier Barais, and Benoit Combemale. 2026. Did You Forkget It? Detecting One-Day Vulnerabilities in Open-Source Forks with Global History Analysis. In *Conference on Software Supply Chain Offensive Research and Ecosystem Defenses (SCORED '26), October 06, 2026, Prague, Czech Republic*. ACM, New York, NY, USA, 11 pages. https://doi.org/10.1145/3848003.3848009

## Abstract

Tracking vulnerabilities inherited from third-party open-source software is a well-known challenge, often addressed by tracing the threads of dependency information. At scale, existing approaches precompute the vulnerable versions of software associated with known CVEs, based on declared impacted versions or using local history analysis. However, vulnerabilities can also propagate through *forking*: a repository forked after a vulnerability is introduced but before it is patched may remain vulnerable long after the original repository has been fixed. Existing *history analysis* approaches analyze only the repository referenced by the CVE, providing a local view of the ecosystem that excludes forks sharing part of its development history. Vulnerabilities disclosed and patched elsewhere in a fork ecosystem may therefore persist as one-day (known but unpatched) vulnerabilities without fork maintainers' awareness. This paper proposes a *global history analysis* approach that conforms to the evaluation semantics defined by the OpenSSF Open Source Vulnerability (OSV) format, while extending them from repository-local histories to the global commit graph of the open-source ecosystem. Leveraging the graph of public code captured by Software Heritage, our approach propagates vulnerability introduction and fix information across shared commit histories and performs automated impact analysis. Starting from 7162 repositories containing vulnerable commits listed in the OSV.dev vulnerability database, we propagate vulnerability information to 2.2 million forks. We evaluate our approach on a sample of 195 ⟨fork, vulnerability⟩ pairs from popular repositories, manually auditing their code and contacting their maintainers for confirmation and responsible disclosure. This process identified 135 high-severity one-day vulnerabilities, achieving a precision of 0.69, with 9 cases confirmed by maintainers.

**Keywords:** one-day vulnerabilities, software supply chain security


---

The reproduction package is composed of multiple tasks that are orchestrated using luigi scheduler [Luigi Scheduler](https://luigi.readthedocs.io/en/stable/central_scheduler.html). For each research question we will detail the associated tasks.


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
