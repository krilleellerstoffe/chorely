package shared.transferable;

/**
 * Reward is a class that represent a reward in the application.
 * version 1.0 2020-04-08
 *
 * @autor Emma Svensson
 */
public class Reward implements Transferable {
    private String name;
    private int rewardPrice;
    private String description;


    public Reward(String name, int rewardPrice) {
        this.name = name;
        this.rewardPrice = rewardPrice;
    }

    
}
