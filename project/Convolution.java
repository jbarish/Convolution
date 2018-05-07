package project;

import java.util.*;

import java.net.*;
import java.io.*;
import org.jtransforms.fft.DoubleFFT_1D;

public class Convolution{
    private double[] impulse;
    private DoubleFFT_1D fft;
    private double[] overlap;
    private int fftLen;
    private int lastOverlap;
    private paraConvolution pc;
    
    private double[] fftIn;
  
    public Convolution(double[] impulse){
	this(impulse, impulse.length*2-1);
    }
    public Convolution(double[] imp, int fftLen ){

	overlap = new double[fftLen];
	fft = new DoubleFFT_1D(fftLen);
	this.fftLen=fftLen;
	lastOverlap=0;

        
        impulse = new double[fftLen];
	System.arraycopy(imp, 0, impulse, 0, imp.length);
	fft.realForward(impulse);
	//yeilds fft with fftLen/2 real and fftlen/2 imag

	//fftIn= new double[fftLen];

    }
    public Convolution(double[] imp, int fftLen, int sampleLen, int num_threads){
	pc = new paraConvolution(imp, sampleLen, num_threads);

	overlap = new double[fftLen];
	fft = new DoubleFFT_1D(fftLen);
	this.fftLen = fftLen;
	lastOverlap=0;
	
    }
    
    public double[] convolve(double[] input, int start, int len){

	double[] fftIn = new double[fftLen];
	//System.out.println("Start: " + start + ", len: " + len + ", size: " + input.length);
	System.arraycopy(input, start, fftIn, 0, len);
	fft.realForward(fftIn);
	
	//special since fftIn[1] is not fftIn[0]'s complex partner
	fftIn[0] = fftIn[0]*impulse[0];
	
	for(int i = 1; i <fftLen/2; i++){
	    double realIn = fftIn[2*i];
	    double imIn = fftIn[2*i+1];
	    double realImp = impulse[2*i];
	    double imImp = impulse[2*i+1];
        
	    double temp = realIn*realImp - imIn*imImp;
	    fftIn[2*i+1] = realIn*imImp + imIn*realImp;
	    fftIn[2*i] = temp;
	    
	}

	// if the len is odd, the complex part of last sample is in fftIn[1]
	if(fftIn.length%2 ==1){
	    
	    double temp = fftIn[fftIn.length-1] *impulse[fftIn.length-1]-
		fftIn[1]*impulse[1];
	    
	    fftIn[1]= fftIn[fftIn.length-1]*impulse[1] +
		fftIn[1]*impulse[fftIn.length-1];
	    
	    fftIn[fftIn.length-1]=temp;
	}else{
	    //if it is even, last real part is in fft[1]
	    fftIn[1] *=impulse[1];
	}


	fft.realInverse(fftIn, true);

	for(int i = 0; i < lastOverlap; i++){
	    fftIn[i]+= overlap[i];
	}
	
	for(int i = len; i < fftLen; i++){
	    
	    overlap[i-len] = fftIn[i];
	}
	lastOverlap=fftLen-len;

	return fftIn;
	
    }

     public double[] paraConvolve(double[] input, int start, int len)throws Exception{
	 //compute the convolution in parallel, then just add old overlap
	 double[] res = pc.paraConvolve(input, start, len);
	for(int i = 0; i < lastOverlap; i++){
	    res[i]+= overlap[i];
	}
	
	for(int i = len; i < fftLen; i++){
	    
	    overlap[i-len] = res[i];
	}
	lastOverlap=fftLen-len;

	return res;
	
    }

    
    public double[] getOverlap(){

	double[] temp = new double[lastOverlap];
	System.arraycopy(overlap,0,temp,0, lastOverlap);
	return overlap;
    }
    public int getOverlapSize(){
	return lastOverlap;
    }
}
