package erl.bilico.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** 全进程唯一的偏好存储；Cookie 与会话信息都落在这里。 */
val Context.biliDataStore: DataStore<Preferences> by preferencesDataStore(name = "bilico")
