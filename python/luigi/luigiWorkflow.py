import os
import luigi
from luigi.contrib.external_program import ExternalProgramTask
from packageJars import generate_jar
import subprocess




# region DownloadOsv
class DownloadOsvTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    workspaceFolder = luigi.Parameter(
        default=config.get("OsvPreProcess", "workspaceFolder")
    )

    def program_args(self):
        return [
                    "python3",
                    "../osv_preprocess/osv_downloader.py",
                    "--destination-folder",
                    self.workspaceFolder,
                ]
            
    def complete(self):
        import os

        return  os.path.exists(self.workspaceFolder + "/unzip") and len(os.listdir(self.workspaceFolder + "/unzip"))>0


# region ComputeVulnRange
class ComputeVulnRangeTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    vulnRange = luigi.Parameter(default=config.get("OsvPreProcess", "vulnRange"))
    workspaceFolder = luigi.Parameter(
        default=config.get("OsvPreProcess", "workspaceFolder")
    )

    def output(self):
        return luigi.LocalTarget(self.vulnRange)

    def requires(self):
        return [DownloadOsvTask(workspaceFolder=self.workspaceFolder)]

    def program_args(self):
        return [
            "python3",
            "../osv_preprocess/vulnerability_range.py",
            "--folder",
            self.workspaceFolder,
        ]


# region versionGraph
class VersionGraphTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))

    requiredList = [".ef"]#".node2type.bin",".cmph",".property.content.is_skipped.bits

    def output(self):
        return [luigi.LocalTarget(self.graphPath + x) for x in self.requiredList]

    def program_args(self):
        return ["swh", "graph", "reindex", self.graphPath]


# region toposort
class TopoSortTask(luigi.Task):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    topoSortedNodeListPath = luigi.Parameter(
        default=config.get("TopoSortTask", "topoSortedNodeListPath")
    )

    def run(self):

        with open(self.topoSortedNodeListPath, "w") as outfile:
            subprocess.run(
                [
                    "./toposort",
                    "--algorithm",
                    "bfs",
                    "--direction",
                    "forward",
                    self.graphPath,
                ],
                stdout=outfile,
            )
            
    def requires(self):
        return [VersionGraphTask(graphPath=self.graphPath)]

    def output(self):
        return luigi.LocalTarget(self.topoSortedNodeListPath)


# region PackageJar
class PackageJarTask(luigi.Task):
    config = luigi.configuration.get_config()
    main_classes = luigi.Parameter()
    jarDirectory = config.get("PackageJarTask", "jarDirectory")
    javaProjectPath = config.get("PackageJarTask", "javaProjectPath")
    if not main_classes:
        raise ValueError("main_classes is required in config file")

        
    def run(self):
        generate_jar(
            main_class=self.main_classes,
            jar_directory=self.jarDirectory,
            relative_project_path=self.javaProjectPath,
            logger_name='luigi-interface'
        )


    def output(self):
        jar_name = self.main_classes.split(".")[-1]

        return luigi.LocalTarget("./" + self.jarDirectory + "/" + jar_name + ".jar")


# region CommitToOrigin
class CommitToOriginTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    rocksDBPath = luigi.Parameter(
        default=config.get("CommitToOriginTask", "rocksDBPath")
    )
    topoSortedNodeListPath = luigi.Parameter(
        default=config.get("TopoSortTask", "topoSortedNodeListPath")
    )
    backupPath = luigi.OptionalStrParameter(
        default="outputs/CommitToOrigin/.cache/backup/"
    )

    if not graphPath:
        raise ValueError("graphPath is required in config file")
    if not rocksDBPath:
        raise ValueError("rocksDBPath is required")
    if not topoSortedNodeListPath:
        raise ValueError("topoSortedNodeListPath is required in config file")

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/CommitToOriginTopo.jar",
            "-g",
            self.graphPath,
            "-d",
            self.rocksDBPath,
            "-t",
            self.topoSortedNodeListPath,
            "-b",
            self.backupPath,
        ]
    
    


    def requires(self):
        return [
            PackageJarTask(self.config.get("CommitToOriginTask", "mainClass")),
            TopoSortTask(topoSortedNodeListPath=self.topoSortedNodeListPath),
        ]

    def output(self):
        return luigi.LocalTarget(self.rocksDBPath)


# region PreProcess
class PreProcessTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    vulnRange = luigi.Parameter(default=config.get("OsvPreProcess", "vulnRange"))
    preProcessResultPath = luigi.Parameter(
        default=config.get("PreProcessTask", "preProcessResultPath")
    )
    vulnRangeTempPath = luigi.Parameter(
        default=config.get("PreProcessTask", "vulnRangeTempPath")
    )

    if not graphPath:
        raise ValueError("graphPath is required in config file")
    if not vulnRange:
        raise ValueError("vulnRange is required in config file")
    if not preProcessResultPath:
        raise ValueError("preProcessResultPath is required in config file")
    if not vulnRangeTempPath:
        raise ValueError("vulnRangeTempPath is required in config file")

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/PreProcess.jar",
            "-g",
            self.graphPath,
            "-v",
            self.vulnRange,
            "-o",
            self.preProcessResultPath,
            "-t",
            self.vulnRangeTempPath,
        ]
    
    

    def requires(self):
        return [
            PackageJarTask(self.config.get("PreProcessTask", "mainClass")),
            ComputeVulnRangeTask(vulnRange=self.vulnRange),
        ]

    def output(self):
        return [luigi.LocalTarget(
            self.preProcessResultPath
        )]  # vulnRangeTempPath is generated always before preProcessResultPath


# region VulnerableUnpatchedAnalysis
class RangeAnalysisTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    vulnPreprocessRangesPath = luigi.Parameter(
        default=config.get("PreProcessTask", "preProcessResultPath")
    )
    statisticsOutputPath = luigi.Parameter(
        default=config.get("RangeAnalysisTask", "statisticsOutputPath")
    )
    vulnRangeTempPath = luigi.Parameter(
        default=config.get("PreProcessTask", "vulnRangeTempPath")
    )

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/RangeAnalysis.jar",
            "-v",
            self.vulnRangeTempPath,
            "-p",
            self.vulnPreprocessRangesPath,
            "-s",
            self.statisticsOutputPath,
        ]
    

    def requires(self):
        return [
            PackageJarTask(self.config.get("RangeAnalysisTask", "mainClass")),
            PreProcessTask(preProcessResultPath=self.vulnPreprocessRangesPath),
        ]

    def output(self):
        return luigi.LocalTarget(self.statisticsOutputPath)


# region OsvLabel
class OsvLabelTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    preProcessResultPath = luigi.Parameter(
        default=config.get("PreProcessTask", "preProcessResultPath")
    )
    osvLabelResultPath = luigi.Parameter(
        default=config.get("OsvLabelTask", "osvLabelResultPath")
    )

    if not graphPath:
        raise ValueError("graphPath is required in config file")
    if not preProcessResultPath:
        raise ValueError("vulnRange is required in config file")
    if not osvLabelResultPath:
        raise ValueError("osvLabelResultPath is required in config file")

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/OsvLabel.jar",
            "-g",
            self.graphPath,
            "-v",
            self.preProcessResultPath,
            "-o",
            self.osvLabelResultPath,
        ]
    


    def requires(self):
        return [
            PackageJarTask(self.config.get("OsvLabelTask", "mainClass")),
            PreProcessTask(preProcessResultPath=self.preProcessResultPath),
        ]

    def output(self):
        return luigi.LocalTarget(self.osvLabelResultPath+ "/result.bin")
    
    def complete(self):
        import os

        return  os.path.exists(self.osvLabelResultPath + "/result.bin")


# region ProvenanceCompute
class ProvenanceComputeTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    rocksDBPath = luigi.Parameter(
        default=config.get("CommitToOriginTask", "rocksDBPath")
    )
    osvLabelResultPath = luigi.Parameter(
        default=config.get("OsvLabelTask", "osvLabelResultPath")
    )
    vulnerableProvenancePath = luigi.Parameter(
        default=config.get("ProvenanceComputeTask", "vulnerableProvenancePath")
    )

    if not graphPath:
        raise ValueError("graphPath is required in config file")
    if not rocksDBPath:
        raise ValueError("rocksDBPath is required in config file")
    if not osvLabelResultPath:
        raise ValueError("topoSortedNodeListPath is required in config file")
    if not vulnerableProvenancePath:
        raise ValueError("vulnerableProvenancePath is required in config file")

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/ProvenanceCompute.jar",
            "-g",
            self.graphPath,
            "-d",
            self.rocksDBPath,
            "-a",
            self.osvLabelResultPath + "/result.bin",
            "-v",
            self.vulnerableProvenancePath,
        ]
    
    


    def requires(self):
        return [
            PackageJarTask(self.config.get("ProvenanceComputeTask", "mainClass")),
            OsvLabelTask(osvLabelResultPath=self.osvLabelResultPath),
            CommitToOriginTask(rocksDBPath=self.rocksDBPath),
        ]

    def output(self):
        return luigi.LocalTarget(self.vulnerableProvenancePath)


# region RangeAnalyzer
class RangeAnalyzerTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    preProcessResultPath = luigi.Parameter(
        default=config.get("PreProcessTask", "preProcessResultPath")
    )
    vulnerableProvenancePath = luigi.Parameter(
        default=config.get("ProvenanceComputeTask", "vulnerableProvenancePath")
    )
    rangeToOriginPath = luigi.Parameter(
        default=config.get("RangeAnalyzerTask", "rangeToOriginPath")
    )

    if not graphPath:
        raise ValueError("graphPath is required in config file")
    if not preProcessResultPath:
        raise ValueError("topoSortedNodeListPath is required in config file")
    if not vulnerableProvenancePath:
        raise ValueError("vulnerableProvenancePath is required in config file")
    if not rangeToOriginPath:
        raise ValueError("rangeToOriginPath is required in config file")

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/RangeAnalyzer.jar",
            "-g",
            self.graphPath,
            "-v",
            self.vulnerableProvenancePath,
            "-o",
            self.rangeToOriginPath,
            "-r",
            self.preProcessResultPath,
        ]
    
    


    def requires(self):
        return [
            PackageJarTask(self.config.get("RangeAnalyzerTask", "mainClass")),
            ProvenanceComputeTask(
                vulnerableProvenancePath=self.vulnerableProvenancePath
            ),
        ]

    def output(self):
        return luigi.LocalTarget(self.rangeToOriginPath)


# region VulnerableRepoAnalysis
class VulnerableRepoAnalysisTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    vulnerableProvenancePath = luigi.Parameter(
        default=config.get("ProvenanceComputeTask", "vulnerableProvenancePath")
    )
    rangeToOriginPath = luigi.Parameter(
        default=config.get("RangeAnalyzerTask", "rangeToOriginPath")
    )
    jsonOutputPath = luigi.Parameter(
        default=config.get("VulnerableRepoAnalysisTask", "jsonOutputPath")
    )
    binOutputPath = luigi.Parameter(
        default=config.get("VulnerableRepoAnalysisTask", "binOutputPath")
    )

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/VulnerableRepoAnalysis.jar",
            "-g",
            self.graphPath,
            "-v",
            self.vulnerableProvenancePath,
            "-o",
            self.rangeToOriginPath,
            "-j",
            self.jsonOutputPath,
            "-n",
            self.binOutputPath,
        ]
    
    


    def requires(self):
        return [
            PackageJarTask(self.config.get("VulnerableRepoAnalysisTask", "mainClass")),
            RangeAnalyzerTask(rangeToOriginPath=self.rangeToOriginPath),
        ]

    def output(self):
        return luigi.LocalTarget(
            self.jsonOutputPath
        )  # the binOutputPath is always generated first, hence the jsonOutputPath is the only necessary output


# region VulnerableRevisionAnalysis
class VulnerableRevisionAnalysisTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    vulnerableProvenancePath = luigi.Parameter(
        default=config.get("ProvenanceComputeTask", "vulnerableProvenancePath")
    )
    osvLabelResultPath = luigi.Parameter(
        default=config.get("OsvLabelTask", "osvLabelResultPath")
    )
    jsonOutputPath = luigi.Parameter(
        default=config.get("VulnerableRevisionAnalysisTask", "jsonOutputPath")
    )
    binOutputPath = luigi.Parameter(
        default=config.get("VulnerableRevisionAnalysisTask", "binOutputPath")
    )

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/VulnerableRevisionAnalysis.jar",
            "-g",
            self.graphPath,
            "-v",
            self.vulnerableProvenancePath,
            "-a",
            self.osvLabelResultPath + "/result.bin",
            "-j",
            self.jsonOutputPath,
            "-n",
            self.binOutputPath,
        ]
    
    


    def requires(self):
        return [
            PackageJarTask(
                self.config.get("VulnerableRevisionAnalysisTask", "mainClass")
            ),
            ProvenanceComputeTask(
                vulnerableProvenancePath=self.vulnerableProvenancePath
            ),
        ]

    def output(self):
        return luigi.LocalTarget(
            self.jsonOutputPath
        )  # the binOutputPath is always generated first, hence the jsonOutputPath is the only necessary output


# region VulnerableRevisionPerUrlAnalysis
class VulnerableRevisionPerUrlAnalysisTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    vulnerableProvenancePath = luigi.Parameter(
        default=config.get("ProvenanceComputeTask", "vulnerableProvenancePath")
    )
    jsonOutputPath = luigi.Parameter(
        default=config.get("VulnerableRevisionPerUrlAnalysisTask", "jsonOutputPath")
    )
    innerlistStoringPath = luigi.Parameter(
        default=config.get(
            "VulnerableRevisionPerUrlAnalysisTask", "innerListStoringPath"
        )
    )
    threadTmpFolder = luigi.Parameter(
        default=config.get("VulnerableRevisionPerUrlAnalysisTask", "threadTmpFolder")
    )
    rangeToVulnRevBinPath = luigi.Parameter(
        default=config.get("VulnerableRevisionAnalysisTask", "binOutputPath")
    )

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/VulnerableRevisionPerUrlAnalysis.jar",
            "-g",
            self.graphPath,
            "-v",
            self.vulnerableProvenancePath,
            "-j",
            self.jsonOutputPath,
            "-l",
            self.innerlistStoringPath,
            "-t",
            self.threadTmpFolder,
            "-e",
            self.rangeToVulnRevBinPath,
        ]
    
    


    def requires(self):
        return [
            PackageJarTask(
                self.config.get("VulnerableRevisionPerUrlAnalysisTask", "mainClass")
            ),
            ProvenanceComputeTask(
                vulnerableProvenancePath=self.vulnerableProvenancePath
            ),
            VulnerableRevisionAnalysisTask(binOutputPath=self.rangeToVulnRevBinPath),
        ]

    def output(self):
        return luigi.LocalTarget(
            self.jsonOutputPath
        )  # the binOutputPath is always generated first, hence the jsonOutputPath is the only necessary output


# region VulnerableUnpatchedAnalysis
class VulnerableUnpatchedAnalysisTask(ExternalProgramTask):
    config = luigi.configuration.get_config()
    graphPath = luigi.Parameter(default=config.get("MainConfig", "graphPath"))
    vulnerableProvenancePath = luigi.Parameter(
        default=config.get("ProvenanceComputeTask", "vulnerableProvenancePath")
    )
    jsonOutputPath = luigi.Parameter(
        default=config.get("VulnerableUnpatchedAnalysisTask", "jsonOutputPath")
    )
    rangeToVulnRevBinPath = luigi.Parameter(
        default=config.get("VulnerableRevisionAnalysisTask", "binOutputPath")
    )

    def program_args(self):
        return [
            "java",
            f"-Dlogpath=log/{self.get_task_family()}.log",
            "-Xmx"+os.environ.get("SWHOSV_RAM"),
            "-jar",
            "outputs/jars/VulnerableUnpatchedAnalysis.jar",
            "-g",
            self.graphPath,
            "-v",
            self.vulnerableProvenancePath,
            "-j",
            self.jsonOutputPath,
            "-e",
            self.rangeToVulnRevBinPath,
        ]
    
    


    def requires(self):
        return [
            PackageJarTask(
                self.config.get("VulnerableUnpatchedAnalysisTask", "mainClass")
            ),
            ProvenanceComputeTask(
                vulnerableProvenancePath=self.vulnerableProvenancePath
            ),
            VulnerableRevisionAnalysisTask(binOutputPath=self.rangeToVulnRevBinPath),
        ]

    def output(self):
        return luigi.LocalTarget(self.jsonOutputPath)


# region Main
if __name__ == "__main__":
    luigi.build(
        [
            VulnerableRepoAnalysisTask(),
            VulnerableRevisionAnalysisTask(),
            VulnerableRevisionPerUrlAnalysisTask(),
            VulnerableUnpatchedAnalysisTask(),
            OsvLabelTask()
        ]
    )
