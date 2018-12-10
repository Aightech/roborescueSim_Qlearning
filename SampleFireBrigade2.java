package sample;

import static rescuecore2.misc.Handy.objectsToIDs;
import static rescuecore2.misc.java.JavaTools.instantiate;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;

import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.messages.Command;
import rescuecore2.Constants;
import rescuecore2.Timestep;
import rescuecore2.log.Logger;

import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.score.ScoreFunction;


/**
   A sample fire brigade agent.
 */
public class SampleFireBrigade2 extends AbstractSampleAgent<FireBrigade> {
    private static final String MAX_WATER_KEY = "fire.tank.maximum";
    private static final String MAX_DISTANCE_KEY = "fire.extinguish.max-distance";
    private static final String MAX_POWER_KEY = "fire.extinguish.max-sum";

    private int maxWater;
    private int maxDistance;
    private int maxPower;
    private int nbFeatures=7;
    private int nbActions=5;
    private int turn_score =0;
    private int last_hp = 10000;
    private double last_nb_fire_building = 0;
    private double building_weight=10;
    private double hp_weight = 10;
    private Collection<EntityID> unexploredBuildings;


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
        System.out.println("Fire agent 2 lauched");
        unexploredBuildings = new HashSet<EntityID>(buildingIDs);
        
        qlearning.importQvalues("src/sample/Qvalues/modelFire2.txt");
        
        
        //qlearning.importQvalues("test.txt");
        //qlearning.exportQvalues("test2.txt");
}
    

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) 
    {
    	if(time%5==0)
    		qlearning.exportQvalues("src/sample/Qvalues/test.txt");
    	System.out.println("Fire agent think");
    	updateUnexploredBuildings(changed);
    	if(unexploredBuildings.size() < 10)
    		unexploredBuildings = new HashSet<EntityID>(buildingIDs);
    	
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
	    		turn_score -= 10;
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
	            turn_score -= 1;
	            break;
	    	}
	    	case 2://go burning building
	    	{
	    		List<EntityID> path;
	    		 Collection<EntityID> all = getBurningBuildings();
	    		 for (EntityID next : all) 
	    		 {
	    			 if(next != null)
		             {
		            	 path = search.breadthFirstSearch(me().getPosition(), next);
		            	 if(path!=null)
		            	 {
		            		 sendMove(time, path);
		            		 System.out.println("Fire agent do : go burning building");
		            		 break;
		            	 }		            	 
		             }
	    		 }
	    		 turn_score -= 1;
	    		 break;
	             
	    	}
	    	case 3://go random
	    	{
	    		List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
	            if(path==null)
	            	path = randomWalk();
	            sendMove(time, path);
	            System.out.println("Fire agent do : go explore");
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
	                    System.out.println("Fire agent do : extinguish");
	                    break;
	                }
	            }
	            turn_score -= 1;
	            break;
	    	}
	    	
	    	
    	}
    	
    	//loose HP
    	turn_score -= hp_weight*(last_hp - me().getHP());
    	last_hp = me().getHP();
    	
    	Collection<EntityID> all = getBurningBuildings();
    	last_nb_fire_building = all.size() - last_nb_fire_building;
    	turn_score -= building_weight*last_nb_fire_building;
    	last_nb_fire_building += (all.size() - last_nb_fire_building)*0.2;

    	
    }

    protected int[] getState(int time, ChangeSet changed, Collection<Command> heard)
    {
    	for (Command next : heard) {
            Logger.debug("Heard " + next);
        }
    	FireBrigade me = me();
    	int[] state = new int[nbFeatures];
    	
    	
    	// Are we currently full of water?
    	state[0] = (me.isWaterDefined() && me.getWater() < 14000) ? 0:1; //(me.isWaterDefined() && me.getWater() < maxWater && location() instanceof Refuge)?1:0;
        
    	// Are we filled with some water?
    	state[1] = (me.isWaterDefined() && me.getWater() < 3000) ? 0:1;
    	
        // close to a refuge 
        state[2] = 0; 
        for (EntityID ref : refugeIDs) 
        	if(model.getDistance(getID(), ref) < 60000)
        		state[2] = 1; 
        	//System.out.println("dist to refuge : " + model.getDistance(getID(), ref) );
        
        // very close to a refuge 
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        state[3] = (path != null && path.size() < 2) ? 1:0;
        
         // Is there buildings that are on fire
         Collection<EntityID> all = getBurningBuildings();
         System.out.println("building in fire: " + all.toString());
         System.out.println("building not explore: " + Integer.toString(unexploredBuildings.size()));
         state[4] = (all != null && all.size() < 1) ? 0:1;
         
         // // Is there buildings that are on fire and close
         boolean burningClose=false;
         for (EntityID next : all) 
        	 if (model.getDistance(getID(), next) <= maxDistance)
        		 burningClose = true;
         state[5] = (burningClose)?1:0;
         
         //is there a fireman close to us
         boolean close_to_fireBrigade=false;
         for (FireBrigade next : getFireColeague()) 
        	 if (model.getDistance(getID(), next.getID()) <= maxDistance)
        		 close_to_fireBrigade = true;
         state[6] = (close_to_fireBrigade)?1:0;
         
         
         System.out.println("Level water: " + Integer.toString(me.getWater()));
         System.out.println("hp: " + Integer.toString(me.getHP()));
         System.out.println("Fire agent state [" + ((state[0]==0)?"not filling":"filling") + " , "+ ((state[1]==0)?"empty":"filled") + " , "+ ((state[2]==0)?"not close to R":"close to R") + " , "+ ((state[3]==0)?"not very close to R":"very close to R") + " , "+ ((state[4]==0)?"no fire":"building on fire") + " , "+ ((state[5]==0)?"not close to 1":"close to 1") + " , "+ ((state[6]==0)?"not close to 2":"close to 2") + " ] ");

         
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

//                    System.out.println("fire level: " + b.getFieryness());
//                    System.out.println("fire level: " + b.getFieryness());
//                    System.out.println("fire temp: " + b.getTemperature());
//                    System.out.println("fire enum: " + b.getFierynessEnum());
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
    private void updateUnexploredBuildings(ChangeSet changed) {
        for (EntityID next : changed.getChangedEntities()) {
            unexploredBuildings.remove(next);
        }
    }
    
    private List<FireBrigade> getFireColeague() {
        List<FireBrigade> targets = new ArrayList<FireBrigade>();
        for (StandardEntity next : model.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE)) {
        	FireBrigade h = (FireBrigade)next;
            if (h == me()) {
                continue;
            }
            if (h.isHPDefined()
                && h.isBuriednessDefined()
                && h.isDamageDefined()
                && h.isPositionDefined()
                && h.getHP() > 0
                && (h.getBuriedness() > 0 || h.getDamage() > 0)) {
                targets.add(h);
            }
        }
        Collections.sort(targets, new DistanceSorter(location(), model));
        return targets;
    }
    
}

