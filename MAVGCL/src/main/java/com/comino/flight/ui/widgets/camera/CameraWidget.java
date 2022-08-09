/****************************************************************************
 *
 *   Copyright (c) 2017,2021 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.ui.widgets.camera;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.prefs.Preferences;

import org.mavlink.messages.MAV_SEVERITY;
import org.mavlink.messages.MSP_CMD;
import org.mavlink.messages.lquac.msg_msp_command;

import com.comino.flight.FXMLLoadHelper;
import com.comino.flight.file.KeyFigurePreset;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.prefs.MAVPreferences;
import com.comino.flight.ui.panel.control.FlightControlPanel;
import com.comino.flight.ui.widgets.charts.IChartControl;
import com.comino.flight.ui.widgets.panel.ControlWidget;
import com.comino.jfx.extensions.ChartControlPane;
import com.comino.mavcom.control.IMAVController;
import com.comino.mavcom.log.MSPLogger;
import com.comino.mavcom.model.segment.Status;
import com.comino.video.src.IMWVideoSource;
import com.comino.video.src.impl.http.MJpegVideoSource;
import com.comino.video.src.impl.replay.ReplayMP4VideoSource;
import com.comino.video.src.impl.rtps.RTSPMjpegVideoSource;
import com.comino.video.src.mp4.MP4Recorder;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

public class CameraWidget extends ChartControlPane implements IChartControl {

	private int X = 640 ;
	private int Y = 480 ;


	@FXML
	private ImageView        image;



	private IMWVideoSource 	source = null;
	private boolean			big_size=false;
	private FloatProperty  	scroll= new SimpleFloatProperty(0);
	private FloatProperty  	replay= new SimpleFloatProperty(0);


	private MP4Recorder     recorder = null;
	private boolean         isConnected = false;

	private MSPLogger       logger = null;
	private Preferences     userPrefs;

	private ControlWidget   widget;

	private ReplayMP4VideoSource replay_video;
	private final AnalysisModelService model ;



	public CameraWidget() {
		FXMLLoadHelper.load(this, "CameraWidget.fxml");
		image.fitWidthProperty().bind(this.widthProperty());
		image.fitHeightProperty().bind(this.heightProperty());

		replay_video = new ReplayMP4VideoSource();
		model = AnalysisModelService.getInstance();
	}



	@FXML
	private void initialize() {


		this.setFixedRatio((double)Y/X);

		fadeProperty().addListener((observable, oldvalue, newvalue) -> {

			if(newvalue.booleanValue())

				if(state.getReplayingProperty().get() || state.getLogLoadedProperty().get()) {

					if(replay_video.isOpen()) {
						image.setVisible(true);
						Platform.runLater(() -> {
							image.setImage(replay_video.playAt(1.0f));
						});
					} else {
						if(!replay_video.open()) {
							image.setVisible(false);
							widget.getVideoVisibility().setValue(false);
						}
					}


				}
				else  {
					if(!state.getConnectedProperty().get() || !connect())
						return;
					if(source!=null) {
						image.setVisible(true);
						source.start();
					}
				}
			else {
				if(!recorder.getRecordMP4Property().get())
					stopStreaming();
			}
		});

		image.setOnMouseClicked(event -> {

			if(event.getButton().compareTo(MouseButton.SECONDARY)==0) {
				if(!big_size)
					big_size=true;
				else
					big_size=false;
				resize(big_size,X,Y);

			} else {

				if(event.getClickCount()!=2)
					return;

				if(state.getStreamProperty().get() == 0)
					state.getStreamProperty().set(1);
				else
					state.getStreamProperty().set(0);	

			}
			event.consume();
		});


		state.getStreamProperty().addListener((o,ov,nv) -> {

			msg_msp_command msp = new msg_msp_command(255,1);
			msp.command = MSP_CMD.SELECT_VIDEO_STREAM;
			msp.param1  = nv.intValue();
			control.sendMAVLinkMessage(msp);
		});
		


		state.getRecordingProperty().addListener((o,ov,nv) -> {
			if(!userPrefs.getBoolean(MAVPreferences.VIDREC, false) || !state.isAutoRecording().get() || control.isSimulation())
				return;

			if(nv.intValue()==AnalysisModelService.COLLECTING) {

				if(replay_video.isOpen()) 
					replay_video.close();

				if(source==null)
					connect();
				if(!isConnected)
					return;
				if(!source.isRunning())
					source.start();
				Platform.runLater(() -> {
					recorder.getRecordMP4Property().set(true);
					logger.writeLocalMsg("[mgc] MP4 recording started", MAV_SEVERITY.MAV_SEVERITY_NOTICE);
				});
			} else {
				if(recorder.getRecordMP4Property().get()) {
					Platform.runLater(() -> {
						recorder.getRecordMP4Property().set(false);
						logger.writeLocalMsg("[mgc] MP4 recording stopped", MAV_SEVERITY.MAV_SEVERITY_NOTICE);
						if(!fadeProperty().getValue())
							stopStreaming();
					});
				}
			}

		});

		scroll.addListener((v, ov, nv) -> {
			if(replay_video.isOpen() && image.isVisible()) {
				Platform.runLater(() -> {
					image.setImage(replay_video.playAt(nv.floatValue()));
				});
			}
		});

		replay.addListener((v, ov, nv) -> {
			int data_size = model.getModelList().size();
			if(replay_video.isOpen() && image.isVisible()) {
				Platform.runLater(() -> {
					image.setImage(replay_video.playAt(nv.floatValue()/data_size));
				});
			}
		});



		state.getLogLoadedProperty().addListener((v,o,n) -> {
			if(n.booleanValue()) {
				stopStreaming();
				if(replay_video.open()) {
					Platform.runLater(() -> {
						image.setImage(replay_video.playAt(1.0f));
					});
				} else
					widget.getVideoVisibility().setValue(false);

			} else {
				replay_video.close();
				if(state.getConnectedProperty().get()) {
					if(!connect())
						return;
					source.start();
				}
				else
					image.setVisible(false);
			}

		});
	}

	public FloatProperty getScrollProperty() {
		return scroll;
	}



	private void resize(boolean big, int maxX, int maxY) {
		Platform.runLater(() -> {
			if(big) {
				this.setInitialHeight(maxY); 
				this.setInitialWidth(maxX);
				this.setMaxHeight(maxY);
				this.setMaxWidth(maxX);
			} else
			{
				this.setInitialHeight(maxY/2); this.setMaxHeight(maxY/2);
				this.setInitialWidth(maxX/2); this.setMaxWidth(maxX/2);
			}
		});
	}

	public void setup(IMAVController control,FlightControlPanel flightControl) {
		this.control = control;
		this.widget  = flightControl.getControl();
		userPrefs = MAVPreferences.getInstance();
		logger = MSPLogger.getInstance();
		recorder = new MP4Recorder(userPrefs.get(MAVPreferences.PREFS_DIR, System.getProperty("user.home")),X,Y);
		ChartControlPane.addChart(91,this);
		
		state.getCurrentUpToDate().addListener((v,o,n) -> {
			float p = 0; 
		//	if(!n.booleanValue()) {
				p = (float)(model.getCurrent().dt_sec) / (float)(model.getLast().dt_sec);	
//			} else {
//				p = 1-scroll.floatValue()/100;
//			}
			if(!replay_video.isOpen())
				return;
//			System.out.println(p+"/"+scroll.floatValue());
			final Image img = replay_video.playAt(p);
			Platform.runLater(() -> {
				image.setImage(img);
			});
		});

	}




	@Override
	protected void perform_action() {
		widget.getVideoVisibility().setValue(false);
	}       

	private void stopStreaming() {
		if(isConnected && source != null) {
			source.stop();
		}
	}


	private boolean connect() {
		String url_string = null;

		if(isConnected)
			return true;


		if(userPrefs.get(MAVPreferences.PREFS_VIDEO,"http://%:8080/mjpeg").contains("%"))
			url_string = userPrefs.get(MAVPreferences.PREFS_VIDEO,"http://%:8080/mjpeg").replace("%", control.getConnectedAddress());
		else
			url_string = userPrefs.get(MAVPreferences.PREFS_VIDEO,"none");

		try {
			URI url = new URI(url_string);

			System.out.println(url.toString());

			if(url.toString().startsWith("http")) {
				//				source = new StreamVideoSource(url,AnalysisModelService.getInstance().getCurrent());
				source = new MJpegVideoSource(url,model.getCurrent());
			} 
			else if(url.toString().startsWith("rtsp")) {
				source = new RTSPMjpegVideoSource(url,AnalysisModelService.getInstance().getCurrent());
			}
			else {
				System.out.println("Streaming protocol not supported");
				return false;
			}
			source.addProcessListener((im, fps, tms) -> {
				if(isVisible())
					Platform.runLater(() -> {
						image.setImage(im);

					});
			});
			source.addProcessListener(recorder);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return false;
		}

		isConnected = true;
		return true;
	}

	@Override
	public IntegerProperty getTimeFrameProperty() {
		return null;
	}

	@Override
	public FloatProperty getReplayProperty() {
		return replay;
	}

	@Override
	public BooleanProperty getIsScrollingProperty() {
		return null;
	}

	@Override
	public void refreshChart() {

	}

	@Override
	public KeyFigurePreset getKeyFigureSelection() {
		return null;
	}

	@Override
	public void setKeyFigureSelection(KeyFigurePreset preset) {

	}
}
