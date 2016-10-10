/*
 * Copyright 2015 Junxue Zhang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.edu.seu.cat.metrics

import java.io.{File, FileOutputStream}
import java.util.zip.ZipInputStream

import cn.edu.seu.cat.common.util.log.Logging
import cn.edu.seu.cat.common.util.{OsCheck, Utils}
import org.hyperic.sigar.Sigar

/**
 * Created by JunxueZhang on 5/13/15.
 */

object SigarUtil extends Logging {

  val sigarFolderName = "sigar_lib"

  val sigarFolder = new File(sigarFolderName)

  if (!sigarFolder.exists) {

    sigarFolder.mkdir

    val is = Utils.getResource("sigar_lib.zip").openStream
    val zis = new ZipInputStream(is)

    val buffer = new Array[Byte](1024)

    logInfo("Start to extract sigar native library")

    Stream.continually(zis.getNextEntry).takeWhile(_ != null).filter {
      !_.isDirectory
    }.foreach { entry =>
      val entryName = entry.getName
      val os = new FileOutputStream(s"$sigarFolderName/$entryName")
      Stream.continually(zis.read(buffer)).takeWhile(_ != -1).foreach { count =>
        os.write(buffer, 0, count)
      }
      os.close
    }

    zis.close
    is.close
  }

  val seperator = if (OsCheck.getOperatingSystemType == OsCheck.OSType.Windows) ";" else ":"
  val path = s"${System.getProperty("java.library.path")}$seperator${sigarFolder.getCanonicalPath}"
  System.setProperty("java.library.path", path)

  val sigar = new Sigar()

  def getSigar:Sigar = {
    sigar
  }
}