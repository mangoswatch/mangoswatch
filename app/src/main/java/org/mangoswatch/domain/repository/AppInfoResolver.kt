package org.mangoswatch.domain.repository

import org.mangoswatch.domain.model.AppIdentity

interface AppInfoResolver {
    fun resolve(uid: Int): AppIdentity
}
