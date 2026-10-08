import io.github.aw1y2z.sesame.model.task.goldenbeans.*;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
public class Review {public static void main(String[] a)throws Exception{
 goldenbeansRpcCall.checkIn("sign");goldenbeansRpcCall.fire("task","TRIGGER");goldenbeansRpcCall.submitTask("task");goldenbeansRpcCall.claimAward("task");goldenbeansRpcCall.grabBean("SUCCESS","item");goldenbeansRpcCall.drawLottery();goldenbeansRpcCall.exchangeBean(1);
 assert ApplicationHook.writes==7;
 ApplicationHook.spent=ApplicationHook.writes=0;
 GoldenBeansExchange.exchangeSesame(0,100);
 assert ApplicationHook.spent==100 && Status.tracked==100;
 GoldenBeansExchange.exchangeSesame(0,100);
 assert ApplicationHook.spent==100 && Status.tracked==100;
 for(boolean farm:new boolean[]{false,true})for(boolean missing:new boolean[]{false,true}){
  ApplicationHook.spent=ApplicationHook.writes=Status.tracked=0;ApplicationHook.unknown=!missing;ApplicationHook.missingDelta=missing;
  for(int i=0;i<2;i++)if(farm)GoldenBeansExchange.exchangeManure(0,100);else GoldenBeansExchange.exchangeSesame(0,100);
  assert ApplicationHook.spent==100 && ApplicationHook.writes==1 && Status.tracked==100:"unknown exchange exceeded configured daily quota";
 }
 ApplicationHook.unknown=ApplicationHook.missingDelta=false;
 for(boolean farm:new boolean[]{false,true})for(Object valid:new Object[]{100,"100"}){
  ApplicationHook.spent=ApplicationHook.writes=Status.tracked=0;ApplicationHook.invalidDelta=valid;
  for(int i=0;i<2;i++)if(farm)GoldenBeansExchange.exchangeManure(0,100);else GoldenBeansExchange.exchangeSesame(0,100);
  assert ApplicationHook.spent==100 && ApplicationHook.writes==1 && Status.tracked==100:"valid integer delta rejected";
 }
 for(boolean farm:new boolean[]{false,true})for(Object invalid:new Object[]{1.5,"1.5",4294967297L,"4294967297"}){
  ApplicationHook.spent=ApplicationHook.writes=Status.tracked=0;ApplicationHook.invalidDelta=invalid;
  for(int i=0;i<2;i++)if(farm)GoldenBeansExchange.exchangeManure(0,100);else GoldenBeansExchange.exchangeSesame(0,100);
  assert ApplicationHook.spent==100 && ApplicationHook.writes==1 && Status.tracked==100:"malformed delta refunded reserved quota: "+invalid;
 }
 System.out.println("PASS single golden bean writes and failed sync/unknown ACK/missing or malformed delta retain both entry daily quotas");
}}
