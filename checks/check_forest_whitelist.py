"""Run actual forest whitelist predicate and verify every collection route shares its guard."""
from pathlib import Path
import subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
forest='model/task/antForest/AntForestV2.java'
src=(SOURCE/forest).read_text(encoding='utf-8')
assert 'new BooleanModelField("collectWhiteListMode"' in src,'Missing optional whitelist mode'
guard=method(forest,'private boolean allowCollectByWhiteList(')
collect=method(forest,'private void collectEnergy(CollectEnergyEntity collectEnergyEntity, Boolean joinThread, String username)')
assert collect.count('allowCollectByWhiteList(')>=3,'Must check enqueue, callback entry and after throttle wait'
assert collect.rindex('allowCollectByWhiteList(',0,collect.index('ApplicationHook.requestObject'))>collect.index('TimeUtil.sleep(sleep)')
assert 'allowCollectByWhiteList(userId)' in method(forest,'private JSONObject collectUserEnergy(')
assert 'allowCollectByWhiteList(friendId)' in method(forest,'private void findAndCollectEnergy(')
code='''import java.util.*;public class ForestWhitelistCheck {
 static class Bool{boolean n;boolean getValue(){return n;}}static class ListField{Set<String> ids=new HashSet<>();Set<String> getValue(){return ids;}}
 Bool collectWhiteListMode=new Bool();ListField collectWhiteList=new ListField();Set<String> dontCollectMap=new HashSet<>();String selfId="self";
 @@GUARD@@
 public static void main(String[] args){ForestWhitelistCheck f=new ForestWhitelistCheck();
 assert f.allowCollectByWhiteList("friend");f.dontCollectMap.add("old-black");assert f.allowCollectByWhiteList("old-black"):"off must preserve legacy PK exception";
 f.collectWhiteListMode.n=true;assert f.allowCollectByWhiteList("self");assert !f.allowCollectByWhiteList("friend");assert !f.allowCollectByWhiteList(null)&&!f.allowCollectByWhiteList("");
 f.collectWhiteList.ids.addAll(Arrays.asList("friend","old-black"));assert f.allowCollectByWhiteList("friend")&&!f.allowCollectByWhiteList("old-black");
 f.collectWhiteList.ids.clear();assert !f.allowCollectByWhiteList("friend"):"empty whitelist must not collect everyone";
 f.collectWhiteListMode.n=false;assert f.allowCollectByWhiteList("friend");
 System.out.println("PASS opt-in/self/empty/unknown/list changes/blacklist precedence, main/find-energy/PK and deferred shared collect guards");}}
'''.replace('@@GUARD@@',guard)
with tempfile.TemporaryDirectory(prefix='sesame-white-list-') as tmp:
 f=Path(tmp)/'ForestWhitelistCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(f)],check=True)
 subprocess.run(['java','-ea','-cp',tmp,'ForestWhitelistCheck'],check=True)
