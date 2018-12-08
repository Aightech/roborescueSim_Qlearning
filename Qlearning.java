package sample;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Scanner;

import rescuecore2.log.Logger;
import rescuecore2.standard.entities.StandardEntityURN;

public class Qlearning {
	
	/**features[
    level of water : 0 1
    level of damage closest damaged building : 0 1
    distance closest damaged building 0 1
    level of damage most populated building : 0 1 
    distance closest damaged building 0 1
    distance to refuge 0 1
    life : 1 2
    ]
    **/

    private int m_NB_features;
    private int m_N_feature[];
    private int m_sumN_feature[];
    private int m_NB_states;
    private int m_NB_actions;
    
    private double m_gamma;
    private double m_lambda;
    private double[][] m_Qvalues;
    
    private int[] m_past_state;
    private int[] m_state;
    private int m_action;
    private double m_reward;
    private double m_temperature;
    private double[] m_probaAction;
    
    
    
    
    public Qlearning(int nbFeatures, int[] rangeFeatures, int nbAction, double lambda, double gamma, double temperature) {
		m_NB_features = nbFeatures;
        m_N_feature = rangeFeatures;
        m_sumN_feature = new int [m_NB_features+1];
        m_NB_actions = nbAction;

        m_lambda = lambda;
        m_gamma = gamma;
        
        m_past_state = new int[m_NB_features];
        m_state = new int[m_NB_features];
        m_action = 0;
        m_reward = 0;
        m_temperature = temperature;
        m_probaAction = new double[m_NB_actions];
        
        
        m_sumN_feature[0]=1;
        //System.out.println(m_sumN_feature[0]);
        for(int i = 0; i < m_NB_features; i++)
              	m_sumN_feature[i+1] = m_N_feature[i]*m_sumN_feature[i];
        	//System.out.println(m_sumN_feature[i+1]);
        
        m_NB_states = m_sumN_feature[m_NB_features];
        //System.out.println(m_NB_states);
        m_Qvalues = new double [m_NB_states][m_NB_actions];
        
    }
    
    
    public void update()
    {
    	//System.out.println("Update Qlearning");
    	
    	double Q = computeQvalue(m_past_state, m_action, m_state, m_reward);
    	//System.out.println("state : " + Integer.toString(m_state[0]) + " action: " + Integer.toString(m_action) );
    	//System.out.println("size : " + Integer.toString(m_Qvalues.length));
    	m_Qvalues[indexQstate(m_state)][m_action] = Q;
    	
    	m_action = 4;//chooseAction();
    	System.out.println("action : " + Integer.toString(m_action));
    	m_past_state = m_state;
    	m_reward = 0;
    
    }
    
    /**
     * Update the current state of the agent
     */
    public void setNewState(int[] state)
    {
    	if(state.length < m_NB_features)
    		System.out.println("Size error of the state given in arg");
    	else
    		for(int i = 0; i < m_NB_features; i++)
    			m_state[i] = state[i];
    }
    
    public void setNewReward(double rew)
    {
    	m_reward = rew;
    }
    
    /**
     * Choose the action to do in a given state
     * @return
     */
    public int getNewAction()
    {
    	return m_action; 
    }
    
    public void importQvalues(String fileName) 
    {
    	Scanner scan;
        File file = new File(fileName);
        try {
            scan = new Scanner(file);
            m_NB_states = scan.nextInt();
            m_NB_actions = scan.nextInt();
            m_Qvalues = new double [m_NB_states][m_NB_actions];
            for(int s = 0; s < m_NB_states; s++)
	    		for(int a = 0; a < m_NB_actions; a++)
	    			m_Qvalues[s][a] = scan.nextDouble();
            
        } catch (FileNotFoundException e1) {
                e1.printStackTrace();
        }
    }
    
    public void exportQvalues(String fileName)
    {
    	FileWriter writer;
		try {
			writer = new FileWriter(fileName);
			writer.write(Integer.toString(m_NB_states) + " " + Integer.toString(m_NB_actions) + "\n");
			for(int s = 0; s < m_NB_states; s++)
	    	{
	    		for(int a = 0; a < m_NB_actions; a++)
	    		{
	    			writer.write(Double.toString(m_Qvalues[s][a]));
	    			writer.write(" ");
	    		}
	    		writer.write("\n");
	    	}
			
	    	writer.write("The second line");
	    	writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
    	
    }
    
    /**
     * return the index in the Qtable of the given state
     * @param state current state
     * @return the index of the state
     */
    private int indexQstate(int[] state)
    {
    	int index = 0;
    	for(int i = 0; i< m_NB_features;i++)
    	{
    		index += m_sumN_feature[i]*state[m_NB_features-1-i];
    	}
    	//System.out.println("Index : " + Integer.toString(index));
    	return index; 
    }
    
    /**
     * Compute the new Q value
     * @param Qvali
     * @return
     */
    private double computeQvalue(int[] past_state, int past_action, int[] new_state , double reward)
    {
    	double max_Q_new=0;
    	return m_lambda * (reward + m_gamma * max_Q_new + (1 - m_lambda)* m_Qvalues[indexQstate(past_state)][past_action]);
    	//return Qval_new; 
    }
    
    /**
     * Choose the action to do in a given state
     * @return
     */
    private int chooseAction()
    {
    	int action = 0;
    	double explore = Math.random();
    	//if(explore < m_explore_exploit_ratio)
    		
    	return action; 
    }
    
    private void computeProbaAction() 
    {
    	double denominator = 0;
    	int indexState = indexQstate(m_past_state); 
    	for(int i = 0 ; i < m_NB_actions ; i++)
    		denominator += Math.exp(- m_Qvalues[indexState][i]/ m_temperature);
    	
    	m_probaAction[0] = Math.exp(- m_Qvalues[indexQstate(m_past_state)][0]/ m_temperature) / denominator;
    	for(int i = 1 ; i < m_NB_actions ; i++)
    		m_probaAction[i] = m_probaAction[i-1] + Math.exp(- m_Qvalues[indexQstate(m_past_state)][i]/ m_temperature) / denominator;
    	
    	double choice = Math.random();
    	for(int i = 0 ; i < m_NB_actions ; i++)
    		if(choice < m_probaAction[i])
    		{
    			m_action = i;
    			break;
    		}
    }
    

}

