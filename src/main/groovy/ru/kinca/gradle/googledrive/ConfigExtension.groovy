package ru.kinca.gradle.googledrive

import com.google.api.services.drive.model.Permission
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Extension that allows to configure the plugin in a declarative way.
 *
 * @author Valentin Naumov
 */
class ConfigExtension {
    String destinationFolder

    String destinationName

    File file

    String clientId

    String clientSecret

    List<Permission> permissions = [new Permission().setType('anyone').setRole('reader')]

    Boolean updateIfExists = true
}