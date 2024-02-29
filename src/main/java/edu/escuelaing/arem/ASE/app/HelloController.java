package edu.escuelaing.arem.ASE.app;

@Component
public class HelloController {
    @GetMapping("/hello")
    public static String index(){
        return "Holii";
    }
    @GetMapping("/helloname")
    public static String helloService(String name){
        return ("Holii "+name);
    }
    @GetMapping("/scuare")
    public static String square(String val){

        return ""+(Double.valueOf(val)*Double.valueOf(val));
    }

}
