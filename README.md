# ![nutcat](https://raw.githubusercontent.com/snowzjx/nutcat/master/other/logo/nutcat.png)

Nutcat is a Pregel-like Graph Processing Framework implemented in Scala.

## How to use Nutcat?

1. Use SBT to pack the whole project for distribution. 
   
   ``` shell
    $ cd cat  
   
    $ sbt   
   
    $ clean   
   
    $ pack
   ```
   
2. Launch master and workers.
   
   ``` shell
    $ cd target/pack/bin
    
    $ start_cat_master
   
    $ start_cat_worker
   ```
   
3. You can use your own config file, please use ```--conf <file>``` to override some entries.
   
   ``` java
    cat.master.actorsystem = "catMasterSystem"  
   
    cat.master.actor = "master"  
   
    cat.master.address = "127.0.0.1"  
   
    cat.master.port = 8888  
   
    cat.worker.actorsystem = "catWorkerSystem"  
   
    cat.worker.actor = "worker"  
   
    cat.worker.address = "127.0.0.1"  
   
    cat.worker.port = 0  
   
    cat.worker.registration.retries = 10  
   
    cat.worker.registration.interval = 2  
   
    cat.worker.timeout = 10  
   
    cat.worker.masterakkaurls = "akka.tcp://catMasterSystem@127.0.0.1:8888/user/master"  
   
    cat.executor.buffersize = 20000  
   
    cat.executor.timeout = 10  
   
    cat.ui.root = "127.0.0.1"  
   
    cat.ui.port = 8080  
   
    cat.ui.proxy.path = "http://127.0.0.1:8080"
   ```
   
4. You can use ```--help``` for more information.
   
   ``` shell
    $ start_cat_master --help
   ```
   
5. Then you can submit your own jar and execute your main class.
   
   ``` shell
   $ submit_cat_job --jar <path_to_your_jar> --mainClass <main_class>
   ```




## How to make your own jar ?

1. You should include ```api_2.11-x.jar``` and ```common_2.11-x.jar``` into your class path.
   
2. Use VertexData object.
   
   ``` scala
   final case class VertexData[VERTEX, DATA](vertexId: VERTEX,
                                             vertexStatus: VertexStatus,
                                             data: DATA,
                                             connectedVertices: Set[VERTEX])
   ```
   
   The ```VERTEX``` is the vertexId type.
   
   The VertexData is the API for one vertex with the Graph.
   
   It consists of the vertex itself and the connected vertices.
   
   It also contains the user defined data.

   VERTEX: The type of vertexId.
   
   DATA: The type of user defined data.
    
   vertexId: The vertexId.
    
   vertexStatus: The status of a vertex, if Active it will paricipate in the superstep.
    
   data: The user defined data.
   
   connectedVertices: The connected vertices.
   
3. Define your own GraphLoader object.
   
   ``` scala
   class SSSPGraphLoader extends GraphLoader[Long, Unit] {
     override def loadGraph(): Array[VertexData[Long, Unit]] = ???
   }
   ```
   
   The DemoVertexLoader has to implements the traits 
   
   ``` scala
   trait GraphLoader[VERTEX, DATA] {
     def loadGraph(): Array[VertexData[VERTEX, DATA]]
   ```
   
   VERTEX: The type of vertexId.
    
   DATA: The type of user defined data.
   
   The loadGraph function is the key to loading the graph.
   
4. Define your own VertexComputer object.
   
   ``` scala
   class SSSPVertexComputer extends VertexComputer[Long, Unit, SSSPMessage, String] with Logging {
     override def compute(superStepId: Long,
                          vertexData: VertexData[Long, Unit],
                          inMessageList: Array[(Long, SSSPMessage)],
                          globalMessage: Option[String],
                          context: VertexContext[Long, Unit, SSSPMessage, String]): Unit = ???
   }
   ```
   
   The VertexComputer has to implements the traits ```VertexComputer[VERTEX, DATA, MSG, GMSG]``` and defines what each vertex has to do in each superstep.
   
   VERTEX: The type of vertexId.
   
   DATA: The type of user defined data.
   
   MSG:  The type of message sent to other vertices.
   
   GMSG: The type of message used for global computation.
   
   Now, we will give the SSSP implementation as an example:
   
   ``` scala
   class SSSPVertexComputer extends VertexComputer[Long, Unit, SSSPMessage, String] with Logging {
     override def compute(superStepId: Long,
                          vertexData: VertexData[Long, Unit],
                          inMessageList: Array[(Long, SSSPMessage)],
                          globalMessage: Option[String],
                          context: VertexContext[Long, Unit, SSSPMessage, String]): Unit = {
       globalMessage match {
         case Some(message) =>
           context.writeNewVertexData(vertexData.voltToHalt)
           logInfo(message)
           return
         case _ =>
       }
       if (vertexData.vertexId == 1L) {
         val message = SSSPMessage(1L, List(1L), 1)
         vertexData.connectedVertices.foreach { vertex =>
           context.writeMessage(message, vertex)
         }
       }
       else if (vertexData.vertexId == 8L) {
         val message = inMessageList.head._2
         val information = s"SSSP path: ${message.visited.mkString("->")}->8, with the length ${message.length + 1}"
         context.writeGlobalMessage(information)
       } else {
         if(!inMessageList.isEmpty) {
           val theMessage = inMessageList.reduce((m1, m2) => if (m1._2.length < m2._2.length) m1 else m2)
           vertexData.connectedVertices.foreach { connectedVertex =>
             context.writeMessage(SSSPMessage(theMessage._2.startsFrom,
               theMessage._2.visited :+ vertexData.vertexId,
               theMessage._2.length + 1), connectedVertex)
           }
         }
       }
       context.writeNewVertexData(vertexData.voltToHalt)
     }
   }
   ```

5. Define your AggregatorComputer object, it has to implements the traits```AggregatorComputer[VERTEX, GMSG]```

   ``` scala
  class SSSPAggregatorComputer extends AggregatorComputer[Long, String] {
    override def compute(superStepId: Long,
                         inMessageList: Array[(Long, String)]): Option[(Long, String)] = ???
   ```

   Now, we just use part of the SSSP implementation as an example.

   ``` scala
   class SSSPAggregatorComputer extends AggregatorComputer[Long, String] {
     override def compute(superStepId: Long,
                          inMessageList: Array[(Long, String)]): Option[(Long, String)] = {
       if (inMessageList.isEmpty) {
         return None
       } else {
         return Some(inMessageList.head)
       }
     }
   }
   ```

6. Write a main class to submit the job.
   
   ``` scala
   GenericOptionsParser.parse(args) match {
     case None => {
       logError("Error")
     }
     case Some((conf: JobConf, otherArgs: Map[String, String])) =>
       println(otherArgs.mkString("-"))
     val job = CatJob.createJob(conf,
       "SSSP Job",
       Some((new HashPartitionStrategy[Long]).getClass),
       Some((new SSSPGraphLoader).getClass),
       Some((new SSSPVertexComputer).getClass),
       Some((new SSSPAggregatorComputer).getClass))
     CatJob.submitJob(job)
   }
   ```
   
   You should use ```CatJob.createJob``` function to create the job.
   
7. Pack your program into a Jar file and submit your job.


## Acknowledgement

- Jiahui Jin (jhjin@seu.edu.cn), supervisor of this project, who gives much constructive advice on how to implement this project.
- Boyang Fan (330301247@qq.com), artist of this project, who designs the impressive nutcat logo.

## Contact me

You can send me emails at: 
[jxzhang@seu.edu.cn](mailto: jxzhang@seu.edu.cn)


## Code Statistics

Files

- Total:      57 files
- Scala:      56 files (98.2%)
- Java:       1 files (1.8%)
- Total size: 154,280 Bytes
- Avg size:   16,983 Bytes
- Avg length: 483 lines


Lines

- Total:      4,270 lines
- Code:       2,507 lines (58.7%)
- Comment:    1,183 lines (27.7%)
- Blank:      580 lines (13.6%)
- Bracket:    371 lines (8.7%)


Characters

- Total:      133,708 chars
- Code:       95,006 chars (71.1%)
- Comment:    38,702 chars (28.9%)
