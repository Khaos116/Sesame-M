"""Isolated policy dependency for older module checks; full policy is tested separately."""
POLICY = r'''
enum Outcome {DONE,RETRY,UNABLE,UNSUPPORTED,FORGED,TRIGGERED,AWARDED,FAILED,TRIED_TODAY,SKIPPED}
static class TaskAttemptPolicy {
    enum ProbeResult {TODO,FINISHED,RECEIVED,GONE,UNKNOWN}
    interface StatusProbe { ProbeResult probe(String key); }
    static boolean isReceived(ProbeResult p) { return p==ProbeResult.RECEIVED || p==ProbeResult.GONE; }
    static class Site {
        String bizKey,scene,prefix; java.util.function.BooleanSupplier forge;
        Site(String list,String prefix,String key,String scene,StatusProbe probe) {
            this.bizKey=key;this.scene=scene;this.prefix=prefix;
        }
        Site(String list,String prefix,String key,String scene,String version,
                java.util.function.BooleanSupplier forge,StatusProbe probe) {
            this(list,prefix,key,scene,probe);this.forge=forge;
        }
    }
    static Outcome handle(String key,String title,java.util.function.BooleanSupplier award,
            java.util.function.Supplier<Outcome> attempt,java.util.function.Consumer<String> log,Site site) {
        if(award!=null) return award.getAsBoolean()?Outcome.AWARDED:Outcome.FAILED;
        Outcome result=attempt.get();
        if(result==Outcome.UNSUPPORTED) {
            if(site.forge!=null && site.forge.getAsBoolean()) return Outcome.TRIGGERED;
            TaskAlternative.trigger(null,null,title,site.bizKey,site.scene,site.prefix,log::accept);
            return Outcome.TRIGGERED;
        }
        return result;
    }
}
'''


def award(method):
    return "static class TaskAward {\n" + method(
        "data/task/TaskAward.java", "public static boolean confirmReceivedOrBlackList(").replace("Consumer<String>", "java.util.function.Consumer<String>") + "\n}"
