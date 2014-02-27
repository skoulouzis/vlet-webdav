package nl.uva.vlet.vfs.webdavfs;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.methods.GetMethod;

/**
 * InputStream wrapper.
 *  
 */ 
public class WebdavInputStream extends InputStream
{
    private GetMethod getMethod=null;
    private InputStream inputStream=null;
    private long numRead=0; 
    
    public WebdavInputStream(GetMethod get_method, InputStream input_stream)
    {
        this.getMethod=get_method; 
        this.inputStream=input_stream; 
    }
    
//    public String getMimetype()
//    {
           // check headers: 
//    }
    
    @Override
    public int read() throws IOException
    {
        int value=this.inputStream.read();
        if (value>=0) 
            this.numRead++; // one byte read;
        return value;
    }
    
    @Override
    public int read(byte[] bytes) throws IOException
    {
        int num=this.inputStream.read(bytes);
        if (num>0) 
            this.numRead+=numRead;
        return num;
    }
    
    @Override
    public int read(byte[] bytes,int off,int len) throws IOException
    {
        int num= this.inputStream.read(bytes,off,len); 
        if (num>0) 
            this.numRead+=numRead;
        return num;
    }
    
    @Override
    public void close() throws IOException
    {
        IOException closeEx=null; 

        if (this.getMethod!=null)
        {
            // use release method: 
            this.getMethod.releaseConnection();
        }
        else
        {
            try
            {
                this.inputStream.close();
            }
            catch (IOException e)
            {
                closeEx=e; 
            }
        }
        
        // finally ? 
        
        if (closeEx!=null)
            throw closeEx; 
                
    }
    
    @Override
    public int available() throws IOException
    {
        return this.inputStream.available();  
    }
    
    @Override
    public long skip(long n) throws IOException
    {
        return this.inputStream.skip(n);  
    }
    
    /** 
     * Return number of bytes currently read 
     * @return
     */
    public long getNumRead()
    {
        return numRead; 
    }

    public void mark(int limit)
    {
        this.inputStream.mark(limit); 
    }
    
    public boolean markSupported()
    {
        return this.inputStream.markSupported();
    }
 
    public void reset()  throws IOException 
    {
        this.inputStream.reset(); 
    }
    
}
