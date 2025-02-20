package serveur_chat_pfm;

import java.io.*;
import java.net.*;
import java.sql.*;
import newBean.*;

public class RunnableTraitement implements Runnable
{
    private Socket CSocket = null;
    private DataInputStream dis = null;
    private DataOutputStream dos = null;
    private BeanBDAccess beanOracle;
    private int port_UDP;
    private String ip_udp;
    
    boolean first = true;
    
    public RunnableTraitement(Socket s, int pu, String ipu)
    {
        CSocket = s;
        port_UDP = pu;
        ip_udp = ipu;
        try
        {
            dis = new DataInputStream(new BufferedInputStream(CSocket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(CSocket.getOutputStream()));
        }
        catch(IOException e)
        {
            System.err.println("RunnableTraitement : Host non trouvé : " + e);
        }
        
        beanOracle = new BeanBDAccess();
        try {
            beanOracle.connexionOracle("localhost", 1521, "COMPTA", "COMPTA", "XE");
        } catch (ClassNotFoundException ex) {
            System.err.println("Class not found " + ex.getMessage());
        } catch (SQLException ex) {
            System.err.println("SQL Exception (oracle)" + ex.getMessage()); 
        } catch (connexionException ex) {
            System.err.println(ex.getNumException() + " -- " + ex.getMessage());
        }
    }

    @Override
    public void run()
    {
        System.out.println("RunnableTraitement : Execution du run");
        
        String reponse = ReceiveMsg();  
        String[] parts = reponse.split("#");
        
        if(parts[0].equals("LOGIN_GROUP"))
        {
            verifLogin(parts);
        }
        else
        {
            SendMsg("ERR#Internal server error");
        }   
    }
    
    public void verifLogin(String[] message)
    {
        ResultSet rs = null;
        try {
            rs = beanOracle.selection("PASSWORD", "PERSONNEL", "LOGIN = '"+message[1]+"'");
        } catch (SQLException ex) {
            System.err.println("Erreur runnable traitement verif login : " + ex);
        }
        
        String pwd = null;
        
        try {
            if(!rs.next())
            {
                SendMsg("ERR#Nom d'utilisateur incorrecte ");
            }
            else
                pwd = rs.getString("PASSWORD");
        } catch (SQLException ex) {
            System.err.println("Error serveur chat line 78 : " + ex);
        }
        
        int digest = hashFunction(message[4] + pwd + message[3]);  
        if(message[2].equals(Integer.toString(digest)))
            SendMsg("ACK#"+ip_udp+"#"+port_UDP);
        else
            SendMsg("ERR#Mot de passe incorrecte");
    }
    
    /* Envoi d'un message au client */
    public void SendMsg(String msg)
    {
        String chargeUtile = msg;
        int taille = chargeUtile.length();
        StringBuffer message = new StringBuffer(String.valueOf(taille) + "#" + chargeUtile);
            
        try
        {               
            dos.write(message.toString().getBytes());
            dos.flush();
        }
        catch(IOException e)
        {
            System.err.println("RunnableTraitement : Erreur d'envoi de msg (IO) : " + e);
        }
    }
    
    /* Réception d'un message du client */
    public String ReceiveMsg()
    {
        byte b;
        StringBuffer taille = new StringBuffer();
        StringBuffer message = new StringBuffer();
        
        try
        {
            while ((b = dis.readByte()) != (byte)'#')
            {                   
                if (b != (byte)'#')
                    taille.append((char)b);
            }
                
            for (int i = 0; i < Integer.parseInt(taille.toString()); i++)
            {
                b = dis.readByte();
                message.append((char)b);
            }  
        }
        catch(IOException e)
        {
            System.err.println("RunnableTraitement : Erreur de reception de msg (IO) : " + e);
        }
            
        return message.toString();
    }
    
    private int hashFunction(String message)
    {
        int hashValue = 0;
        
        for(int i = 0; i < message.length(); i++)
            hashValue += (int)message.charAt(i);
        
        return hashValue%67;
    }
}
