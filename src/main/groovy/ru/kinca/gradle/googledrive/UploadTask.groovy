package ru.kinca.gradle.googledrive

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.http.FileContent
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.services.drive.DriveRequest
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.Permission

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task that uploads specified file to Google Drive. Opens a browser to
 * authorize, if was not authorized before.
 *
 * @author Valentin Naumov
 */
class UploadTask extends DefaultTask {

    ConfigExtension configExtension

    @TaskAction
    void upload() {
        if (!configExtension.credentialFile) {
            throw new GradleException("credentialFile is null.")
        }

        GoogleClient googleClient = new GoogleClient(
                configExtension.clientId,
                configExtension.clientSecret,
                new FileDataStoreFactory(configExtension.credentialFile))

        String destinationFolderId = DriveUtils.makeDirs(
                googleClient.drive, 'root',
                GoogleDriveUploaderPlugin.toPathElements(configExtension.destinationFolder))

        DriveFile driveFile = new DriveFile()
        driveFile.setName(configExtension.destinationName)
        driveFile.setParents([destinationFolderId])

        FileContent content = new FileContent('application/octet-stream', configExtension.file)
        DriveRequest<DriveFile> modificationRequest

        List<DriveFile> existingDestinationFiles = DriveUtils.findInFolder(googleClient.drive, destinationFolderId, configExtension.destinationName)
        if (existingDestinationFiles) {
            if (configExtension.updateIfExists) {
                // Update the most recent, if the are many with the same name
                DriveFile updatedFile = existingDestinationFiles
                        .toSorted { it.getModifiedTime() }.first()

                logger.info("File with name '${configExtension.destinationName}' already" +
                        " exists, id: ${updatedFile.getId()}. Updating...")
                modificationRequest = googleClient.drive.files().update(
                        updatedFile.getId(), null, content)
            } else {
                throw new GradleException('Remote file(s) already exists,' +
                        " id: ${existingDestinationFiles*.getId()}")
            }
        } else {
            logger.info('Creating file...')
            modificationRequest = googleClient.drive.files()
                    .create(driveFile, content)
        }

        modificationRequest.getMediaHttpUploader().with {
            progressListener = {
                logger.info('Uploaded: {} {}[bytes]({})',
                        it.uploadState,
                        String.format('%,3d', it.numBytesUploaded),
                        String.format('%2.1f%%', it.progress * 100))
            }
        }

        DriveFile updated = modificationRequest.execute()

        logger.debug('Creating permissions...')
        BatchRequest permissionsBatchRequest = googleClient.drive.batch()
        configExtension.permissions.each {
            googleClient.drive.permissions().create(updated.getId(), it)
                    .queue(permissionsBatchRequest, new SimpleJsonBatchCallBack(
                    'Could not update permissions'))
        }
        permissionsBatchRequest.execute()

        logger.info("File '${configExtension.file.canonicalPath}' is uploaded to" +
                " '$configExtension.destinationFolder' and named '$configExtension.destinationName'.")
        logger.quiet("Google Drive short link: ${getLink(updated)}")
    }

    private static String getLink(
            DriveFile file) {
        "https://drive.google.com/open?id=${file.getId()}"
    }
}
