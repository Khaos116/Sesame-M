"""Real Agreement persistence and Kotlin UI storage callbacks; no device/network."""
from pathlib import Path
import os
import re
import subprocess
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame"
UI = SOURCE / "ui/miuix"
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"

def block(text, start):
    begin = text.index(start)
    masked = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"', lambda m: ' ' * len(m[0]), text)
    opening = masked.index('{', begin)
    depth = 0
    for end in range(opening, len(masked)):
        depth += (masked[end] == '{') - (masked[end] == '}')
        if depth == 0:
            return text[begin:end+1]
    raise AssertionError(start)

def artifact(group, name, version):
    return next(p for p in (CACHE / group / name / version).glob('*/*.jar') if not re.search(r'-(sources|javadoc)\.', p.name))

with tempfile.TemporaryDirectory(prefix='sesame-ui-actions-') as directory:
    work = Path(directory)
    app_config = work / 'AppConfig.java'
    app_config.write_text('''package io.github.aw1y2z.sesame.data;
import java.util.concurrent.*;
public class AppConfig {
 public static final AppConfig INSTANCE=new AppConfig();
 public static volatile CountDownLatch entered=new CountDownLatch(0),release=new CountDownLatch(0);
 public static boolean fail,throwSave,loadFailed;public int agreementVersion;public long agreementAcceptedAt;
 public boolean isInit(){return true;}public static void load(){}
 public Integer getAgreementVersion(){return agreementVersion;}public void setAgreementVersion(Integer n){agreementVersion=n;}
 public Long getAgreementAcceptedAt(){return agreementAcceptedAt;}public void setAgreementAcceptedAt(Long n){agreementAcceptedAt=n;}
 public static Boolean save(){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("save latch timed out");}catch(InterruptedException e){throw new AssertionError(e);}if(throwSave)throw new IllegalStateException("private-token configuration body");return !fail;}
 public static boolean isLoadFailed(){return loadFailed;}public static Boolean forceSave(){return save();}
}''', encoding='utf-8')
    log = work / 'Log.java'
    log.write_text('package io.github.aw1y2z.sesame.util; public class Log {public static String output=""; public static void i(String s){} public static void printStackTrace(Throwable t){java.io.StringWriter text=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(text));output=text.toString();}}', encoding='utf-8')
    check = work / 'AgreementCheck.java'
    check.write_text('''import java.util.concurrent.*;import io.github.aw1y2z.sesame.data.*;
public class AgreementCheck {
 public static void main(String[] args)throws Exception {
  AppConfig.INSTANCE.agreementVersion=0;AppConfig.INSTANCE.agreementAcceptedAt=123;
  AppConfig.fail=true;AppConfig.entered=new CountDownLatch(1);AppConfig.release=new CountDownLatch(1);
  Thread worker=new Thread(()->{if(Agreement.accept())throw new AssertionError("failed save accepted");});worker.start();
  if(!AppConfig.entered.await(2,TimeUnit.SECONDS))throw new AssertionError("save not entered");
  FutureTask<Boolean> reader=new FutureTask<>(Agreement::isAccepted);new Thread(reader).start();
  try{if(reader.get(200,TimeUnit.MILLISECONDS))throw new AssertionError("unpersisted agreement admitted");}
  finally{AppConfig.release.countDown();worker.join();}
  if(Agreement.isAccepted()||AppConfig.INSTANCE.agreementAcceptedAt!=123)throw new AssertionError("rollback failed");
  AppConfig.throwSave=true;if(Agreement.accept()||Agreement.isAccepted())throw new AssertionError("throwing save admitted");
  String diagnostic=io.github.aw1y2z.sesame.util.Log.output;
  if(diagnostic.contains("private-token")||diagnostic.contains("configuration body"))throw new AssertionError("agreement diagnostic leaked configuration");
  if(!diagnostic.contains("IllegalStateException")||!diagnostic.contains("AppConfig.save"))throw new AssertionError("agreement diagnostic lost exception type or stack");
  AppConfig.throwSave=false;AppConfig.fail=false;if(!Agreement.accept()||!Agreement.isAccepted())throw new AssertionError("retry failed");
  System.out.println("PASS real Agreement slow-save admission, rollback, exception and retry");
 }
}''', encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(SOURCE / 'data/Agreement.java'), str(app_config), str(log), str(check)], check=True, timeout=30)
    subprocess.run(['java', '-ea', '-cp', str(work), 'AgreementCheck'], check=True, timeout=15)

    agreement_ui = (UI / 'AgreementScreen.kt').read_text(encoding='utf-8')
    log_ui = (UI / 'MiuixLogViewerActivity.kt').read_text(encoding='utf-8')
    agree_callback = block(agreement_ui, '                onAgree = {')
    agree_callback = agree_callback[agree_callback.index('{') + 1:-1]
    clear_method = block(log_ui, 'fun clearCurrentLog(')
    if '--baseline-log' in sys.argv:
        old = subprocess.check_output(['git', 'show', 'HEAD:app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixLogViewerActivity.kt'], cwd=ROOT).decode('utf-8')
        callback = block(old, '                onClear = {')
        clear_method = 'fun clearCurrentLog() {' + callback[callback.index('{') + 1:-1] + '}'
    code = r'''
import java.io.*
import java.util.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import io.github.aw1y2z.sesame.data.Agreement
import io.github.aw1y2z.sesame.data.AppConfig
object Boundary {
 lateinit var ui:Thread
 var operation="";var entered=CountDownLatch(0);var release=CountDownLatch(0)
 fun block(op:String){operation=op;entered=CountDownLatch(1);release=CountDownLatch(1)}
 fun touch(op:String){check(Thread.currentThread()!==ui){"storage on main: $op"};if(op==operation){entered.countDown();check(release.await(5,TimeUnit.SECONDS))}}
 fun main(){check(Thread.currentThread()===ui)}
}
object ToastUtil {val messages=mutableListOf<String>();fun show(c:Any,s:String){Boundary.main();messages+=s}}
object FileUtil {
 var fail=false;var exports=0;var clears=0
 fun exportFile(f:File):File?{Boundary.touch("export");exports++;return if(fail)null else f}
 fun clearFile(f:File):Boolean {Boundary.touch("clear");if(fail)return false;f.writeText("");clears++;return true}
}
object RpcFailureJournal {fun fileFor(parent:File?,now:Long):File {Boundary.touch("summary");return File(parent,"summary.json")}}
class FakeLogType(val target:File) {val file:File get(){Boundary.touch("resolve");return target}}
const val COUNTDOWN_SECONDS=15
class AgreementUi(val saveScope:CoroutineScope) {
 val context=Any();var saving=false;var remaining=0;var hasStorage=true;var checked=true;var phraseOk=true;var accepted=0
 fun onAgree(){Boundary.main();accepted++}
 fun click(){@@AGREE@@}
}
@@PARSER@@
private class Reader {
 val delegate=LogTailReader()
 fun poll(f:File):List<LogEntry>{check(Thread.holdsLock(this));Boundary.touch("poll");return delegate.poll(f)}
 fun reload(f:File):List<LogEntry>{check(Thread.holdsLock(this));Boundary.touch("reload");return delegate.reload(f)}
}
private class LogUi(val file:File,val shareScope:CoroutineScope) {
 var currentFile:File?=file;val context=Any();var actionBusy=false;var clearing=false;var readRevision=0L
 var searchQuery="old";var selectedTag:String?="tag"
 val logReader=Reader();val fileUpdateChannel=Channel<Unit>(Channel.CONFLATED)
 var entries=emptyList<LogEntry>();val history=mutableListOf<List<LogEntry>>()
 fun updateEntries(updated:List<LogEntry>,reset:Boolean=false){Boundary.main();entries=updated;history+=listOf(updated)}
 @@REFRESH@@
 @@CLEAR@@
 @@EXPORT@@
 suspend fun resolve(logType:FakeLogType):File? {var resolvedFile:File?=null;@@RESOLVE@@;return resolvedFile}
}
suspend fun settle(scope:CoroutineScope){scope.coroutineContext[Job]!!.children.toList().joinAll()}
fun main()=runBlocking {
 Executors.newSingleThreadExecutor{Thread(it,"test-ui")}.asCoroutineDispatcher().use {ui ->
  withContext(ui){Boundary.ui=Thread.currentThread()}
  val directory=java.nio.file.Files.createTempDirectory("log-actions-").toFile()
  val file=File(directory,"runtime.log");file.writeText("00:00:00.000 [RUNTIME] old\n")
  val logScope=CoroutineScope(SupervisorJob()+ui);val logs=LogUi(file,logScope)
  if(@@BASELINE@@){withContext(ui){logs.clearCurrentLog()};error("baseline unexpectedly passed")}
  Boundary.block("resolve")
  val resolve=logScope.async {logs.resolve(FakeLogType(file))}
  check(Boundary.entered.await(2,TimeUnit.SECONDS));withContext(ui){check(!resolve.isCompleted)}
  Boundary.release.countDown();check(resolve.await()==file);Boundary.operation=""
  withContext(ui){logs.refreshEntries(true)}
  logs.history.clear();Boundary.block("poll")
  val stalePoll=logScope.launch{logs.refreshEntries()}
  check(Boundary.entered.await(2,TimeUnit.SECONDS))
  withContext(ui){logs.clearCurrentLog();logs.clearCurrentLog();check(logs.clearing)}
  Boundary.release.countDown();settle(logScope);stalePoll.join();Boundary.operation=""
  check(FileUtil.clears==1&&file.readText().isEmpty()&&logs.history.all{it.isEmpty()}) {"old poll overwrote clear"}
  check(logs.fileUpdateChannel.tryReceive().isSuccess) {"clear did not schedule catch-up"}
  // Catch-up sees an append whose event was coalesced while clear was running.
  file.appendText("00:00:01.000 [RUNTIME] new\n")
  withContext(ui){logs.refreshEntries()};check(logs.entries.any{it.body.contains("new")})
  val prior=logs.history.size;Boundary.block("poll")
  val old=logScope.launch{logs.refreshEntries()};check(Boundary.entered.await(2,TimeUnit.SECONDS))
  withContext(ui){logs.currentFile=File(directory,"next.log")};Boundary.release.countDown();old.join();Boundary.operation=""
  check(logs.history.size==prior);withContext(ui){logs.currentFile=file}
  Boundary.block("export");val messages=ToastUtil.messages.size
  withContext(ui){logs.exportCurrentLog(false);logs.exportCurrentLog(false)}
  check(Boundary.entered.await(2,TimeUnit.SECONDS));withContext(ui){check(ToastUtil.messages.size==messages)}
  Boundary.release.countDown();settle(logScope);Boundary.operation=""
  check(FileUtil.exports==1&&ToastUtil.messages.size==messages+1)
  withContext(ui){logs.exportCurrentLog(true)};settle(logScope);check(ToastUtil.messages.last().contains("暂无"))
  File(directory,"summary.json").writeText("{}");withContext(ui){logs.exportCurrentLog(true)};settle(logScope);check(FileUtil.exports==2)
  FileUtil.fail=true;withContext(ui){logs.clearCurrentLog()};settle(logScope);check(ToastUtil.messages.last()=="清空失败");FileUtil.fail=false
  Boundary.block("clear");val oldMessages=ToastUtil.messages.size
  withContext(ui){logs.clearCurrentLog()};check(Boundary.entered.await(2,TimeUnit.SECONDS))
  val jobs=logScope.coroutineContext[Job]!!.children.toList();logScope.cancel();Boundary.release.countDown();jobs.joinAll();Boundary.operation=""
  check(file.readText().isEmpty()&&ToastUtil.messages.size==oldMessages)
  // Actual UI callback uses the real Agreement persistence and suppresses destroyed UI effects.
  val saveScope=CoroutineScope(SupervisorJob()+ui);val screen=AgreementUi(saveScope)
  AppConfig.INSTANCE.agreementVersion=0;AppConfig.fail=true;AppConfig.entered=CountDownLatch(1);AppConfig.release=CountDownLatch(1)
  withContext(ui){screen.click();screen.click()};check(AppConfig.entered.await(2,TimeUnit.SECONDS))
  withContext(ui){check(screen.accepted==0&&!Agreement.isAccepted())}
  AppConfig.release.countDown();settle(saveScope);check(screen.accepted==0&&!screen.saving&&!Agreement.isAccepted())
  AppConfig.fail=false;withContext(ui){screen.click()};settle(saveScope);check(screen.accepted==1)
  AppConfig.INSTANCE.agreementVersion=0;AppConfig.entered=CountDownLatch(1);AppConfig.release=CountDownLatch(1)
  withContext(ui){screen.click()};check(AppConfig.entered.await(2,TimeUnit.SECONDS));val saves=saveScope.coroutineContext[Job]!!.children.toList();saveScope.cancel();AppConfig.release.countDown();saves.joinAll()
  check(screen.accepted==1&&Agreement.isAccepted())
  directory.deleteRecursively()
 }
 println("PASS real Kotlin agreement/log callbacks: off-main I/O, cancellation, single-flight, poll-clear ordering, catch-up and stale-file guards")
}
'''
    parser = re.search(r'data class LogEntry\([\s\S]*?\n\)', log_ui)[0] + '\n'
    parser += '\n'.join(re.findall(r'^private const val MAX_(?:TAIL_BYTES|LOG_ENTRIES) = .+$', log_ui, re.M)) + '\n'
    parser += re.search(r'^private val TIME_REGEX = .+$', log_ui, re.M)[0] + '\n'
    parser += block(log_ui, 'private fun readTailText(') + '\n' + block(log_ui, 'private class LogTailReader')
    for marker, value in {
        '@@AGREE@@': agree_callback, '@@PARSER@@': parser,
        '@@REFRESH@@': block(log_ui, 'suspend fun refreshEntries('), '@@CLEAR@@': clear_method,
        '@@EXPORT@@': block(log_ui, 'fun exportCurrentLog('),
        '@@RESOLVE@@': re.search(r'resolvedFile = withContext\(Dispatchers.IO\) \{ logType.file }', log_ui)[0],
        '@@BASELINE@@': str('--baseline-log' in sys.argv).lower(),
    }.items():
        code = code.replace(marker, value)
    kt = work / 'ActionsCheck.kt';kt.write_text(code, encoding='utf-8')
    stdlib = artifact('org.jetbrains.kotlin','kotlin-stdlib','2.4.0')
    coroutine = artifact('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.10.2')
    compiler = [artifact('org.jetbrains.kotlin',name,version) for name,version in [('kotlin-compiler-embeddable','2.4.0'),('kotlin-reflect','1.6.10'),('kotlin-script-runtime','2.4.0'),('kotlin-build-tools-api','2.4.0')]]
    compiler += [stdlib,coroutine,artifact('org.jetbrains','annotations','13.0')]
    cp = os.pathsep.join(map(str,[work,stdlib,coroutine]))
    subprocess.run(['java','-Xmx256m','-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(work),str(kt)],check=True,timeout=90)
    subprocess.run(['java','-ea','-cp',cp,'ActionsCheckKt'],check=True,timeout=30)
