package sample;

import static rescuecore2.misc.Handy.objectsToIDs;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.messages.Command;
import rescuecore2.log.Logger;

import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.FireBrigade;

/**
   A sample fire brigade agent.
 */
public class SampleFireBrigade extends AbstractSampleAgent<FireBrigade> {
    private static final String MAX_WATER_KEY = "fire.tank.maximum";
    private static final String MAX_DISTANCE_KEY = "fire.extinguish.max-distance";
    private static final String MAX_POWER_KEY = "fire.extinguish.max-sum";

    private int maxWater;
    private int maxDistance;
    private int maxPower;
    private int nbFeatures=7;
    private int nbActions=5;
    private int turn_score =0;

    private Qlearning qlearning = new Qlearning(nbFeatures, new int[] {2,2,2,2,2,2,2}, nbActions, 1, 1, 1);

    @Override
    public String toString() {
        return "Sample fire brigade";
    }
    
    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.BUILDING, StandardEntityURN.REFUGE,StandardEntityURN.HYDRANT,StandardEntityURN.GAS_STATION);
        maxWater = config.getIntValue(MAX_WATER_KEY);
        maxDistance = config.getIntValue(MAX_DISTANCE_KEY);
        maxPower = config.getIntValue(MAX_POWER_KEY);
        Logger.info("Sample fire brigade connected: max extinguish distance = " + maxDistance + ", max power = " + maxPower + ", max tank = " + maxWater);
        System.out.println("Fire agent lauched");
        qlearning.exportQvalues("Qvalues/test.txt");
        //qlearning.importQvalues("test.txt");
        //qlearning.exportQvalues("test2.txt");
}
    

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) 
    {
    	System.out.println("Fire agent think");
    	
    	int[] state = getState(time,changed,heard);
    	
    	qlearning.setNewState(state);

    	qlearning.setNewReward(turn_score);
    	
    	qlearning.update();
    	
    	int action = qlearning.getNewAction();
    	
    	turn_score =0;
    	switch(action)
    	{
	    	case 0://do nothing
	    	{
	    		System.out.println("Fire agent do : rest");
	    		sendRest(time);
	    		break;
	    	}
	    	case 1://go refuge
	    	{
	    		List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
	            if (path != null)
	            {
	                Logger.info("Moving to refuge");
	                System.out.println("Fire agent do : go refuge");
	                sendMove(time, path);
	                break;
	            }
	            else
	            	System.out.println("Fire agent didn't find refuge");
	    	}
	    	case 2://go burning building
	    	{
	    		List<EntityID> path;
	    		 Collection<EntityID> all = getBurningBuildings();
	    		 for (EntityID next : all) 
	    		 {
	    			 if(next != null)
		             {
		            	 path = search.breadthFirstSearch(me().getPosition(), all);
		            	 if(path!=null)
		            	 {
		            		 sendMove(time, path);
		            		 break;
		            	 }		            	 
		             }
	    		 }
//	             // Can we extinguish any right now?
//	             for (EntityID next : all) 
//	             {
//	                 if (model.getDistance(getID(), next) <= maxDistance) 
//	                 {
//	                    Logger.info("Extinguishing " + next);
//			    		sendExtinguish(time, next, maxPower);
//			    		System.out.println("Fire agent do : extinguish");
//			            //sendSpeak(time, 1, ("Extinguishing " + next).getBytes());
//			            break;
//	                 }
//	              
//	             }
//	             System.out.println("Fire agent do : no fire ==> random move");
//	             for (EntityID next : all) {
//	                 if (model.getDistance(getID(), next) <= maxDistance) {
//	                     Logger.info("Extinguishing " + next);
//	                     sendExtinguish(time, next, maxPower);
//	                     sendSpeak(time, 1, ("Extinguishing " + next).getBytes());
//	                     return;
//	                 }
//	     }
//	             List<EntityID> path ;
//	             
//	             System.out.println("Fire agent do : no fire ==> random move");
//	
//	             path = randomWalk();
//	             Logger.info("Moving to building");
//	             sendMove(time, path);
//	     		break;
	             
	    	}
	    	case 3://go random
	    	{
	    		List<EntityID> path;
	    		path = randomWalk();
	            sendMove(time, path);
	    		break;
	    	}
	    	case 4://extinguish
	    	{
	    		Collection<EntityID> all = getBurningBuildings();
	            for (EntityID next : all) 
	            {
	                if (model.getDistance(getID(), next) <= maxDistance)
	                {
	                    sendExtinguish(time, next, maxPower);
	                    break;
	                }
	            }
	    	}
	    	
	    	
    	}
    	
    }

    protected int[] getState(int time, ChangeSet changed, Collection<Command> heard)
    {
    	for (Command next : heard) {
            Logger.debug("Heard " + next);
        }
    	FireBrigade me = me();
    	int[] state = new int[nbFeatures];
    	
    	state[0] = (time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY))?0:1;
    	// Are we currently filling with water?
    	state[1] = (me.isWaterDefined() && me.getWater() < maxWater && location() instanceof Refuge)?0:1;
         // Are we out of water?
    	state[2] = (me.isWaterDefined() && me.getWater() == 0) ? 0:1;
    	
        // Head for a refuge
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        state[3] = (path != null) ? 0:1;
         
         // Find all buildings that are on fire
         Collection<EntityID> all = getBurningBuildings();
         // Can we extinguish any right now?
         for (EntityID next : all) {
             if (model.getDistance(getID(), next) <= maxDistance) {
                 Logger.info("Extinguishing " + next);
             }
         }
         // Plan a path to a fire
         for (EntityID next : all) {
             if (path != null) {
                 Logger.info("Moving to target");
       
             }
         }
    	
    	return state; 
    }
    
    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.FIRE_BRIGADE);
    }

    private Collection<EntityID> getBurningBuildings() {
        Collection<StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.BUILDING);
        List<Building> result = new ArrayList<Building>();
        for (StandardEntity next : e) {
            if (next instanceof Building) {
                Building b = (Building)next;
                if (b.isOnFire()) {
                    result.add(b);
                }
            }
        }
        // Sort by distance
        Collections.sort(result, new DistanceSorter(location(), model));
        return objectsToIDs(result);
    }

    private List<EntityID> planPathToFire(EntityID target) {
        // Try to get to anything within maxDistance of the target
        Collection<StandardEntity> targets = model.getObjectsInRange(target, maxDistance);
        if (targets.isEmpty()) {
            return null;
        }
        return search.breadthFirstSearch(me().getPosition(), objectsToIDs(targets));
    }
}

