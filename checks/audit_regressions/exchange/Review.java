import io.github.aw1y2z.sesame.model.task.goldenbeans.*;
import io.github.aw1y2z.sesame.util.*;
public class Review {public static void main(String[] a){
 GoldenBeansExchange.exchangeSesame(0,100);
 assert goldenbeansRpcCall.spent==100 && Status.tracked==100;
 GoldenBeansExchange.exchangeSesame(0,100);
 assert goldenbeansRpcCall.spent==100 && Status.tracked==100;
 System.out.println("PASS failed sync preserves successful exchange quota");
}}
