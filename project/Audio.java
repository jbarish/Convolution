package project;
import javax.sound.sampled.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.concurrent.*;
import java.util.*;
import org.jtransforms.fft.DoubleFFT_1D;
import org.jtransforms.fft.BenchmarkDoubleFFT;
public class Audio{
    private static final int BYTES_PER_SAMPLE = 4;                // 16-bit audio
    private static final int BITS_PER_SAMPLE = 16;                // 16-bit audio
    private static final int  CHANNELS = 2;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final double MAX_16_BIT = Short.MAX_VALUE;     // 32,767
    private static final int SAMPLE_RATE = 48000;                 // CD-quality audio
    private static final int SAMPLE_BUFFER_SIZE =20000;
    private static final int OUTPUT_BUFFER_SIZE=1024;

    private static int bufferCounter = 0;
    private static int bufferCounterDouble = 0;
    private static byte[]outputBuffer;
    private static double[]outputBufferDouble;
    
    private static byte[] buffer;         // our internal buffer
    private static SourceDataLine srcLine;   // to play the sound
    private static TargetDataLine tgtLine;   // to record the sound

    public static Semaphore writeMutex = new Semaphore ( 1 );


    private static AudioFormat getAudioFormat(){
	  AudioFormat format = new AudioFormat((float) SAMPLE_RATE, BITS_PER_SAMPLE, CHANNELS, SIGNED, BIG_ENDIAN);
	  return format;
    }
    public static int init(){

	try {
            // 44,100 samples per second, 16-bit audio, mono, signed PCM, little Endian
          
	    AudioFormat af = getAudioFormat();
            DataLine.Info srcInfo = new DataLine.Info(SourceDataLine.class,af );

	    DataLine.Info tgtInfo = new DataLine.Info(TargetDataLine.class, af);

            srcLine = (SourceDataLine) AudioSystem.getLine(srcInfo);
            srcLine.open(af, SAMPLE_BUFFER_SIZE * BYTES_PER_SAMPLE);

	    tgtLine = (TargetDataLine) AudioSystem.getLine(tgtInfo);
	    tgtLine.open(af,SAMPLE_BUFFER_SIZE * BYTES_PER_SAMPLE);
	    
           
            buffer = new byte[SAMPLE_BUFFER_SIZE];
	    outputBuffer = new byte[OUTPUT_BUFFER_SIZE*BYTES_PER_SAMPLE];
	    outputBufferDouble = new double[1000000];
        } catch (Exception e) {
            System.out.println(e.getMessage());
	    return 1;
        }

        srcLine.start();
	tgtLine.start();
	return 0;
    }

    //read in as much as possible, up to SAMPLE_BUFFER_SIZE
    //    public static double[][] read(){
    public static SoundInfo read(){
	boolean print = false;
	if(tgtLine.available()>0){
	    System.out.print("TGT:" + tgtLine.available());
	    print = true;
	    
	}
	//makes use of existing buffer
	int cnt = tgtLine.read(buffer, 0, buffer.length < tgtLine.available() ? buffer.length : tgtLine.available());

	long startTime = System.nanoTime();
	if(cnt ==0){
	    //	    return new double[2][0];
	    return new SoundInfo(new double[2][0], startTime);
	}
	if(cnt>0)
	    System.out.println( ", Read: " + cnt);
        
	int bits = getAudioFormat().getSampleSizeInBits();
	if(bits !=  BITS_PER_SAMPLE){
	    throw new RuntimeException("Invalid bit rate. Expected " + BITS_PER_SAMPLE +", found " + bits);
	}
	
	//code below from https://stackoverflow.com/questions/29560491/fourier-transforming-a-byte-array
	double max = Math.pow(2, bits - 1);
	ByteBuffer bb = ByteBuffer.wrap(buffer);
	bb.order(getAudioFormat().isBigEndian() ?
		 ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

	double[] samples = new double[cnt * 8 / bits];
	// convert sample-by-sample to a scale of
	// -1.0 <= samples[i] < 1.0

	
	for(int i = 0; i < samples.length; ++i) {
	    samples[i] = ( bb.getShort() / max );
	}

	double[][] out;
	//if stereo, then left/right alternate samples
	if(getAudioFormat().getChannels()==2){
	    out= new double[2][samples.length/2];
	}else{
	    out= new double[2][samples.length];
	}
	int j=0;
	for(int i =0; i < samples.length; i++, j++){
	    out[0][j]=samples[i];
	    if(getAudioFormat().getChannels()==2){
		i++;
	    }
	    out[1][j]=samples[i];
	}

	return new SoundInfo(out, startTime);
    }
    
  

    

    //write an array of doubles, in mono
    public static void  write( double[] inLeft, int count){
	write(inLeft, null, count);
    }
    
    //write an array of doubles to the sound card, in stereo format
    //operates on it's own thread
    public static void  write( double[] inLeft, double[] inRight, int count){
	try{
	    /*if(srcLine.available() > 10000){
		System.out.println(srcLine.available());
		}*/
	    writeMutex.acquire(); //grab the right to write to the sound card
	    new Thread()
	    {
		public void run() {
		    for(int i = 0; i < count; i++){

			//clamp all samples to -1 to 1
			if(inLeft[i] > 1){
			    inLeft[i] =1;
			}
			if(inLeft[i] < -1){
			    inLeft[i] = -1;
			}

			//if stereo, also clamp right channel
			if(inRight != null){
			    if(inRight[i] > 1){
				inRight[i] =1;
			    }
			    if(inRight[i] < -1){
				inRight[i] = -1;
			    }
			}

			//convert to bytes and write out
			short s = (short) (MAX_16_BIT*inLeft[i]);
			byte b = (byte)s;
			write(b);
			b= (byte) (s >> 8);
			write(b);
			
			if(inRight != null){
			    s = (short) (MAX_16_BIT*inRight[i]);
			    b = (byte)s;
			    write(b);
			    b= (byte) (s >> 8);
			    write(b);
			}
			
		    }
		    writeMutex.release(); //release the sound card
		    return;
		}
	    }.start();
	    
	}
	catch (InterruptedException ie){
	    ie.printStackTrace();
	}
	
    }



    public static void  write( List<double[]> lefts, List<double[]> rights, int count){
	write(lefts, rights, count, 0);
    }
    //write an array of doubles to the sound card, in stereo format
    //operates on it's own thread
    public static void  write( List<double[]> lefts, List<double[]> rights, int count, long startTime){

	    
	    new Thread()
	    {
		public void run() {
		    try{
			writeMutex.acquire(); //grab the right to write to the sound card
			for(int j = 0; j < lefts.size(); j++){
			    double[] inLeft = lefts.get(j);
			    double[] inRight = lefts.get(j);
			    for(int i = 0; i < count; i++){
				
				//clamp all samples to -1 to 1
				if(inLeft[i] > 1){
				    inLeft[i] =1;
				}
				if(inLeft[i] < -1){
				    inLeft[i] = -1;
				}
				
				//if stereo, also clamp right channel
				if(inRight != null){
				    if(inRight[i] > 1){
					inRight[i] =1;
				    }
				    if(inRight[i] < -1){
					inRight[i] = -1;
				    }
				}
				
				//convert to bytes and write out
				short s = (short) (MAX_16_BIT*inLeft[i]);
				byte b = (byte)s;
				write(b);
				b= (byte) (s >> 8);
				write(b);
				
				if(inRight != null){
				    s = (short) (MAX_16_BIT*inRight[i]);
				    b = (byte)s;
				    write(b);
				    b= (byte) (s >> 8);
				    write(b);
				}
				
			    }
			    flush();
			    long endTime = System.nanoTime();
			    long duration = (endTime - startTime);
			    System.out.println("Total Packet Len took: " + duration/1000000.0 + "ms");
			}
		    }
		    catch (InterruptedException ie){
			
			ie.printStackTrace();
		    }
		    writeMutex.release(); //release the sound card
		    return;
		}
	    }.start();
	    
    }
    
    
    public static void  write( byte[] in, int count){
	/*if(srcLine.available() > 10000){
	    System.out.println(srcLine.available());
	    }*/
	//amount to write must be integer multiple of frame size
	if(count%BYTES_PER_SAMPLE==0){
	    srcLine.write(in, 0, count);
	}else{
	    srcLine.write(in, 0, count-(count%BYTES_PER_SAMPLE));
	}
  }


   

     //write a byte to sound card
    public static void write(byte b){

	//if we can, add byte to our internal buffer
	if(bufferCounter < outputBuffer.length){
	    outputBuffer[bufferCounter] = b;
	    bufferCounter++;
	}

	//when buffer is full, send to soundcard
	if (bufferCounter == outputBuffer.length){
	    write(outputBuffer, bufferCounter);
	    bufferCounter = 0;
	}
    }


    //force what is in the byte buffer to be written
    public static void flush(){
	write(outputBuffer, bufferCounter);
	bufferCounter = 0;
    }

    //playback loop
    public static void echo(){
	Audio.init();
	int a = 0;
	while (true){
	   	    
	    System.out.println("TGT:" + tgtLine.available()+ "A=" + a);
	     
	     int cnt = tgtLine.read(buffer, 0, buffer.length < tgtLine.available() ? buffer.length : tgtLine.available());
	   
	     //int cnt = tgtLine.read(buffer, 0, buffer.length);
	    if(cnt>0){
		System.out.println(cnt);
		System.out.println(tgtLine.available());
		System.out.println("src" + srcLine.available());
		srcLine.write(buffer, 0, cnt);
	    
	
	    }
	    a++;
	    
	}
    }
    
    //read file as a byte array, in mono
    private static byte[] readFileByte(String filename) {
        byte[] data = null;
        AudioInputStream ais = null;
        try {
            URL url = Audio.class.getResource(filename);
            ais = AudioSystem.getAudioInputStream(url);
            data = new byte[ais.available()];
            ais.read(data);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException("Could not read " + filename);
        }

        return data;
    }

    //read in as double array, in stereo form
    public static double[][] readFileDouble(String filename) {

	byte[] data = null;
        AudioInputStream ais = null;
	AudioFormat fmt;
	 
        try {
            URL url = Audio.class.getResource(filename);
            ais = AudioSystem.getAudioInputStream(url);
	    fmt = ais.getFormat();
            data = new byte[ais.available()];
            ais.read(data);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException("Could not read " + filename);
        }
	

	int bits = fmt.getSampleSizeInBits();
	if(bits !=  BITS_PER_SAMPLE){
	    throw new RuntimeException("Invalid bit rate. Expected " + BITS_PER_SAMPLE +", found " + bits);
	}
	
	//code below from https://stackoverflow.com/questions/29560491/fourier-transforming-a-byte-array
	double max = Math.pow(2, bits - 1);
	ByteBuffer bb = ByteBuffer.wrap(data);
	bb.order(fmt.isBigEndian() ?
		 ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

	double[] samples = new double[data.length * 8 / bits];
	// convert sample-by-sample to a scale of
	// -1.0 <= samples[i] < 1.0
	System.out.println("Provided Audio Format: " + fmt);
	
	for(int i = 0; i < samples.length; ++i) {
	    samples[i] = ( bb.getShort() / max );
	}
	/////////////////////////////////////////////////////////////////////////////

	//create output data
	double[][] out;

	//if stereo, then left/right alternate samples
	if(fmt.getChannels()==2){
	    out= new double[2][samples.length/2];
	}else{
	    System.out.println("  Converting input to stereo.");
	    out= new double[2][samples.length];
	}
	int j=0;
	for(int i =0; i < samples.length; i++, j++){
	    out[0][j]=samples[i];
	    if(fmt.getChannels()==2){
		i++;
	    }
	    out[1][j]=samples[i];
	}
	return out;
    }

    public static void writeToFile(double d, String fileName){
	saveToFile(d, fileName);
    }

    
    public static void saveToFile(double d, String fileName){
	
	//if we can, add byte to our internal buffer
	if(bufferCounterDouble < outputBufferDouble.length){
	    outputBufferDouble[bufferCounterDouble] = d;
	    bufferCounterDouble++;
	    System.out.println(bufferCounterDouble);
	}
	
	//when buffer is full, send to soundcard
	if (bufferCounterDouble == outputBufferDouble.length){
	    saveToFile(outputBufferDouble, bufferCounterDouble, fileName);
	    bufferCounterDouble = 0;
	}
    }


    // save double array as .wav 
    public static void saveToFile(double[] input, int len, String filename) {

        // assumes 44,100 samples per second
        // use 16-bit audio, mono, signed PCM, little Endian
        AudioFormat format =getAudioFormat();
        byte[] data = new byte[2 * len];
        for (int i = 0; i < len; i++) {
            int temp = (short) (input[i] * MAX_16_BIT);
            data[2*i + 0] = (byte) temp;
            data[2*i + 1] = (byte) (temp >> 8);
        }

        // now save the file
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            AudioInputStream ais = new AudioInputStream(bais, format, len);
            if (filename.endsWith(".wav") || filename.endsWith(".WAV")) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(filename));
		System.out.println("Done!");
		System.exit(1);
            }
            else {
                throw new RuntimeException("File format not supported: " + filename);
            }
        }
        catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }
    }



    
    public static int disconnect(){
	try{
	    tgtLine.drain();
	    tgtLine.close();
	    srcLine.drain();
	    srcLine.close();

	}catch(Exception ex){
	    System.out.println(ex);
	    return 1;
	}
	return 0;
    }

 
  
}


