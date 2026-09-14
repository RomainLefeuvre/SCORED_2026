from google.cloud import storage
import os
import zipfile
import shutil
import pathlib
import click
import logging
logger = logging.getLogger(__name__)
"""
Download all osv all.zip files from 'bucket_name' to 'destination_folder'
"""
def download_bucket_files(bucket_name, destination_folder):
    matching_string = "all.zip"
    #sub ecosystem identifier, we want to exclude those matching this string, since it will result in duplicated osv
    exclude_string = ":"
    logger.info(f"Downloading OSV zipped to {destination_folder}")
    client = storage.Client.create_anonymous_client()
    bucket = client.bucket(bucket_name)
    blobs = bucket.list_blobs()
    for blob in blobs:
        destination_file = f"{destination_folder}/{blob.name}"
        if matching_string in blob.name:
            if exclude_string not in blob.name:
                os.makedirs(os.path.dirname(destination_file),exist_ok=True)
                blob.download_to_filename(destination_file)
                logger.info(f"Downloaded {blob.name} to {destination_file}")
            else:
                logger.info(f"Skip {blob.name}, sub ecosystem")

"""
Unzip all files from the source_folder and save them to the destination_folder
"""
def unzip_files(source_folder, destination_folder):
    logger.info(f"Unzipping files from {source_folder} to {destination_folder}")
    for root, dirs, files in os.walk(source_folder):
        for name in dirs:
            src_dir = os.path.join(root, name)
            dest_dir = src_dir.replace(source_folder, destination_folder)
            os.makedirs(dest_dir, exist_ok=True)

        for name in files:
            src_file = os.path.join(root, name)
            dest_file =  src_file.replace(source_folder, destination_folder)
            dest_file = pathlib.Path(dest_file).parent
            if name.lower().endswith('.zip'):
                with zipfile.ZipFile(src_file, 'r') as zip_ref:
                    zip_ref.extractall(dest_file)
            else:
                shutil.copy2(src_file, dest_file)



@click.command()
@click.option('--destination-folder', default="./out", help='Destination folder to save the files.')
def main(destination_folder:str):
    log_folder="log"
    if not os.path.exists(log_folder):
        os.makedirs(log_folder)
    logging.basicConfig(format='%(levelname)s %(asctime)s %(message)s', datefmt='%m/%d/%Y %I:%M:%S %p',filename=f'{log_folder}/osv_downloader.log', level=logging.INFO)
    bucket_name = "osv-vulnerabilities"
    raw_path=destination_folder+"/raw"
    unzip_path=destination_folder+"/unzip"
    download_bucket_files(bucket_name,raw_path)
    unzip_files(raw_path, unzip_path)

if __name__ == '__main__':
    main()