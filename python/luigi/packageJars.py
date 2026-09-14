import os
import subprocess
import logging

def generate_jar(main_class: str, jar_directory: str, relative_project_path: str, logger_name:str):
    # List of main classes
    logger = logging.getLogger(logger_name)
    # Get the directory path of the script
    script_dir = os.path.dirname(os.path.abspath(__file__))

    project_path = os.path.join(script_dir, relative_project_path)
    jar_directory = os.path.join(script_dir, jar_directory)

    jar_name = f"{main_class.split('.')[-1]}"
    result=subprocess.run(
        [
            "./mvnw",
            "clean",
            "package",
            "-DskipTests=true",
            f"-Djar.name={jar_name}",
            f"-Dmain.class={main_class}",
            f"-Djar.directory={jar_directory}",
        ],
        cwd=project_path,
        check=True,
        capture_output=True,
        
    )
    # Decode the output from bytes to strings
    stdout_output = result.stdout.decode('utf-8')
    stderr_output = result.stderr.decode('utf-8')
    logger.info('Program stdout:\n{}'.format(stdout_output))
    if result.stderr:
        logger.error('Program stderr:\n{}'.format(stderr_output))
        raise Exception("Error while execution, see log")

    logger.info(f"Generated JAR: {jar_name}")

    logger.info("JAR generation completed.")
