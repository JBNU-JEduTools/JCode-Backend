package org.jbnu.jdevops.jcodeportallogin.service.watcher

import org.jbnu.jdevops.jcodeportallogin.entity.Assignment

internal fun Assignment.watcherHwName(): String = dirName.ifBlank { name }
