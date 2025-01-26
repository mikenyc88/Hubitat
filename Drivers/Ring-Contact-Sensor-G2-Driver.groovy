/*
// Version :		0.1.1
// Version date : 	26 Jan 2025
//
// GitHub Url : 	https://github.com/mikenyc88/Hubitat/
// Author Profile :	https://community.hubitat.com/u/mikenyc/summary
// Community Docs : https://community.hubitat.com/t/149189
// Donation Url :	https://account.venmo.com/u/Mike--Dam
//
// License URL : 	https://github.com/mikenyc88/Hubitat/blob/main/LICENSE
// License Type :	GNU Affero General Public License v3.0
// License Desc. :	Permissions of this strongest copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights. When a modified version is used to provide a service over a network, the complete source code of the modified version must be made available. (See link or published license material for full details)
//
// General Notes : I put in comments for beginners to learn the basics of Z-Wave driver coding. I hope you find it useful.
//
// Release notes :
		0.1.0  : Initial BETA Release
*/

import groovy.transform.Field

//metadata is required for all drivers, defines the capabilities of the device, and enables proper pairing
metadata {

    definition (name: "Ring Alarm Contact Sensor G2 - Advanced Driver", namespace: "MikeNYC88", author: "Mike Dam", importUrl: "https://raw.githubusercontent.com/mikenyc88/Hubitat/refs/heads/main/Drivers/Ring-Contact-Sensor-G2-Driver.groovy", singleThreaded : false) {
        //singleThreaded isn't needed & defaults to false. I included purely for educational purposes. If 'true', simultaneous execution of a particular driver instance is prevented. The hub will load driver data (including state), run the called method, and save the data (including state) completely before moving on to any additional calls that may have been queued in the meantime. This applies to "top level" methods only.
        //singleThreaded can be used as a more efficient alternative to atomicState, by preventing more than one overlapping wake of the driver code for the same device.
        
        
        //Add capabilities so the Hub can find them when you are searching for devices that have the capability
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "Battery"
        capability "ContactSensor"
        capability "TamperAlert"
        capability "PushableButton"
		
        //Add commands for : 1) Ability to control device via a rule (considered  a 'custom action'), 2) ability to control device from device detail's commands section
        command "push", [[name: "buttonNumber", type: "NUMBER", description: "1 will always be used, no matter what number is inputted."],[name: "Note", type: "", description: "This command is only here so you can use this device as a button in Rules. It will show as Button 1 is pushed. Device isn't notified of this command's use."]]
        	//The command 'Push' was already included in the capability "PushableButton", but I wanted to customize the description, thus, I added it anyway. This does not create a duplicate.
        command "oneTimeShot", [[name: "delay", type: "NUMBER", description: "5..65535"],[name: "Note 1", type: "", description: "This prompts the sensor to send a wakeup notification one time after this parameter's number of seconds."],[name: "Note 2", type: "", description: "Pushing the button on the device initiates a wake up."]]
        	//oneTimeShot is actually a configuration for the device, but acts as a 1 time action, thus put it here in commands
        command "configure", [[name: "Note", type: "", description: "This will reset all states on the driver & resend the preferences to the device. <b>Note:</b> This is a sleeping device, so these new configurations will not take affect until the device wakes up again (not just status change)."]]
        	//The command 'configure' was already included in the capability "Configuration", but I wanted to customize the description, thus, I added it anyway. This does not create a duplicate.
        command "pollDeviceData", [[name: "Note 1", type: "", description: "This will poll the device for status & device information (firmware, etc.). <b>Note:</b> This is a sleeping device, so polling will not happen until the device wakes up again (not just status change)."]]     
        
        //Add attributes so Rules & Apps know what attributes it can subscribe to
        attribute "buttonPushTime", "STRING"
        //attribute "battery", "NUMBER" 					//Not added since included in the capability "Battery"
        //attribute "contact ", "ENUM", ["closed", "open"] 	//Not added since included in the capability "ContactSensor"
        //attribute "tamper", "ENUM", ["clear", "detected"] //Not added since included in the capability "TamperAlert"
        //attribute "numberOfButtons ", "NUMBER" 			//Not added since included in the capability "PushableButton"
        //attribute "pushed  ", "NUMBER" 					//Not added since included in the capability "PushableButton"
                
		//Fingerprints help during pairing. To generate a fingerprint for a device already paired to the hub, switch to the "Device" driver, run the "Get Info" command, and observe the "fingerprint" output in "Logs." These can also be built manually if you know the required information ("clusters" corresponds to "command classes" for Z-Wave). 
        fingerprint  mfr:"0346", prod:"0201", deviceId:["0301", "0401", "0601"], inClusters: "0x5E,0x6C,0x55,0x9F,0x59,0x85,0x80,0x70,0x5A,0x7A,0x87,0x72,0x8E,0x71,0x73,0x86,0x84" , outClusters: "0x80,0x70,0x72,0x71,0x6C,0x86,0x84", deviceJoinName: "Ring Alarm Contact Sensor G2" 
        //inClusters:"0x5E,0x6C,0x55,0x9F", secureInClusters: "0x59,0x85,0x80,0x70,0x5A,0x7A,0x87,0x72,0x8E,0x71,0x73,0x86,0x84" // There is no real need to separate out the secure vs insecure clusters. The device should tell the hub which are secure upon pairing. 
        //Ensure all inClusters are shown in the Device Details -> Device Info for proper operation
        //Note that a command class's cluster (in HEX) can be found in Hubitat's 'Z-Wave Classes' developer documentation
    }
    preferences {
        //Preferences act as settings for the device. None come with capabilities, all have to be entered. Please see your device's "configurations" command class inputs (see z-wave alliance's product page or manufacturer's tech manual) for all possible settings (configurations) for the device. Also, you put any settings here you want to use to customize your driver's behavior.
        input name: "wakeUpInterval", type: "number", title: "Wake Up Interval (seconds)", defaultValue: 43200, range: "1..*", required: true, description: "Wake-up Interval: Device wakes up, communicates with the controller, and handles pending commands. This is different than heartbeat & should be infrequent to save on battery. Default is 43200."
        input name: "supervisoryReportDelay", type: "number", title: "Supervisory Report Delay (milliseconds)", defaultValue: 1000, range: "1..*", required: true, description: "This should always be less than 'Supervisory Report Timeout' while leaving enough leeway for processing time. Recommended greater than 1000ms. Driver Default : 1000. <b>Note 1</b> LED indication will be delayed this amount. <b>Note 2</b> Delay is needed if Protocol Version = '7.17'. If delay is too low, LED will go red, and device will become unresponsive/erratic/unpredictable."
        input name: "logDebugEnable", type: "bool", title: "Enable debug logging", defaultValue: true, required: true
        input name: "logInfoEnable", type: "bool", title: "Enable info logging", defaultValue: true, required: true
        //It is recommended that you seperate out the device's configurable parameters. This way they can be called upon later easier.
        configParams.each { input it.value.input }
	}
}

//*************Status references*************
//List out all of the device's configurable parameters, as per the device's Z-Wave alliance's product page and/or manufacturer's tech manual
@Field static Map configParams = [
        1: [input: [name: "heartbeatInterval", type: "number", title: "Heartbeat Interval (minutes)", required: true, description: "Heartbeats are automatic battery reports on a timer after the last event. This parameter is the number of minutes between heartbeats. <b>Default & MAX : 70</b>", range: "1..70", defaultValue: 70], parameterSize: 1],
    	2: [input: [name: "applicationRetries", type: "enum", title: "Application Retries", required: true, description: "Number of application level retries attempted for messages either not ACKed or messages encapsulated via supervision get that did not receive a report. This parameter is the number of application level retries. Default : 1", options: [[0:"0"], [1:"1"], [2:"2"], [3:"3"], [4:"4"], [5:"5"]], defaultValue: 1], parameterSize: 1],
    	3: [input: [name: "retryWaitTime", type: "number", title: "Application Level Retry Base Wait Time Period (Seconds)", required: true, description: "The number base seconds used in the calculation for sleeping between retry messages. This parameter is the number base seconds used in the calculation for sleeping between retry messages. Default : 5, <b>MAX : 60</b>", range: "1..60", defaultValue: 5], parameterSize: 1],
    	4: [input: [name: "enableLED", type: "enum", title: "LED Indicator", required: true, description: "This parameter allows a user, via software, to configure the various LED indications on the device. Manufacturer Default : 'On Open', Driver Default : 'On Open & Close'", defaultValue: 2, options: [[0:"No LED"],[1:"On Open"],[2:"On Open & Close"]]], parameterSize: 1],
    	6: [input: [name: "supervisoryReportTimeout", type: "number", title: "Supervisory Report Timeout (milliseconds)", required: true, description: "The number of milliseconds waiting for a Supervisory Report response to a Supervisory Get encapsulated command from the sensor before attempting a retry. This parameter is the number of milliseconds waiting for a Supervisory Report response to a Supervisory Get. Manufacturer Default : 1500, Driver Default : 5000, <b>MAX : 5000</b>", range: "500..5000", defaultValue: 5000], parameterSize: 2]
]

//These command classes do not use security, as per the device's documentation (checked in device info after pairing). Thus their commands do not need to be encrypted. I list these out so that there is no ambiguity to the Hubitat code. 
//zwaveSecureEncap(cmd) is supposed to know if cmd should be encrypted or not based on the command class & the device's pairing parameters... but  I didn't trust it.
//This is a list of the command classes' HEX designation... refer to Hubitat's 'Z-Wave Classes' developer documentation
@Field static List UNSECURE_CLASSES=[
	0x5E, // Z-Wave Plus Info V2 
	0x6C, // Supervision
	0x55, // Transport Service V2 
	0x9F  // Security S2 
	]

//These are the versions of the command classes used by the devices. Required Command Classes are listed in the device's documentation (z-wave alliance & manufacturer tech manual)
//This is a list of the command classes' HEX designation... refer to Hubitat's 'Z-Wave Classes' developer documentation
@Field static Map CMD_CLASS_VERS=[
    0x5E:2, // Z-Wave Plus Info V2 
    0x80:2, // Battery V2
    0x70:4, // Configuration V4 
    0x5A:1, // Device Reset Locally 
    0x7A:5, // Firmware Update Meta-Data V5 
    0x87:3, // Indicator V3 
    0x72:2, // Manufacturer Specific V2 
    0x8E:3, // Multi-Channel Association V3 
    0x71:8, // Notification V8 
    0x73:1, // Powerlevel 
    0x9F:1, // Security S2 
    0x6C:1, // Supervision 
    0x55:2, // Transport Service V2 
    0x86:3, // Version V3 
    0x84:2, // Wake Up V2
    0x59:3, // Association Group Information V3
    0x85:2  // Association V2
	]

//These are taken from the Z-wave class info (standardized for all Zwave devices) & are noted in Hubitat's 'Z-Wave Classes' developer documentation
@Field static Map ZWAVE_NOTIFICATION_TYPES=[
        0:"Reserverd",
        1:"Smoke",
        2:"CO",
        3:"CO2",
        4:"Heat",
        5:"Water",
        6:"Access Control",
        7:"Home Security",
        8:"Power Management",
        9:"System",
        10:"Emergency",
        11:"Clock",
        12:"First"
]

//*************Required/Recommended Methods*************
//Installed() runs when the device is 1st paired (REQUIRED)
void installed() {
    initializeVars()
}

//Uninstalled() is run when the device is uninstalled (REQUIRED)
void uninstalled() {
	trace("In Method : uninstalled()")
}

//Updated() is run when Preferences are saved (REQUIRED)
void updated() {
    trace("In Method : updated()")
    info("updated...")
    info("Debug logging is: ${logDebugEnable == true}")
    info("Info logging is: ${logInfoEnable == true}")
    unschedule()
    //Below I make sure that the preferences stay within given limits. The 'range' feature of the inputs wasn't working for me.
    if(heartbeatInterval>70){
        device.updateSetting("heartbeatInterval", [value: 70, type: "number"])
        debug("heartbeatInterval can't be more than 70. Setting to 70.")
    }
    if(retryWaitTime>60){
        device.updateSetting("retryWaitTime", [value: 60, type: "number"])
        debug("retryWaitTime can't be more than 60. Setting to 60.")
    }
    //Below I make sure that a given setting can't be more than another given setting. 
    if((supervisoryReportTimeout > 5000) || (supervisoryReportDelay > supervisoryReportTimeout)){
        def newSupTimeout = supervisoryReportTimeout
        def newSupDelay
        if (supervisoryReportTimeout>5000) {
            newSupTimeout = 5000
            device.updateSetting("supervisoryReportTimeout", [value: newSupTimeout, type: "number"])
            debug("supervisoryReportTimeout can't be more than 5000. Setting to 5000.")
        }
        if (supervisoryReportDelay > newSupTimeout) {
            newSupDelay = newSupTimeout - 200 // give lee-way
            device.updateSetting("supervisoryReportDelay", [value: newSupDelay, type: "number"])
            debug("supervisoryReportDelay can't be more than supervisoryReportTimeout. Setting it to 200 ms below supervisoryReportTimeout.")
        }        
    }
    //I set a 1 millisecond delay to ensure that all settings are updated prior to running the runConfigs() method. It tells the hub to schedule an method & run it whenever it gets a chance to, which is after the current updates are made. It is uncertain if singleThread would of solved this as well, since these arne't states (they are preferences/settings).
    runInMillis(1,"runConfigs", [misfire: "ignore"])
}

// The Configure capability/command is always good to reset the device as if you refreshly installed it (erasing past states/variables & reinitializing them) and to resend all configurations. This helps stop malfunctioning or erratic behavior.
void configure() {
    trace("In Method : configure()")
    clearAllAttributesAndStates()
    runInMillis(1,"initializeVars", [misfire: "ignore"])
    runInMillis(2,"runConfigs", [misfire: "ignore"])
    runInMillis(3,"pollDeviceData", [misfire: "ignore"])
}

// The method assists in the configure() method & can technially be included within it since it isn't called elsewhere. Separated for educational purposes.
void clearAllAttributesAndStates() {
	trace("In Method : clearAllAttributesAndStates()")
    // Clear all attributes dynamically
	device.currentStates.each { state -> 
        device.deleteCurrentState("${state.name}")
	}
	// Clear state variables
    state.clear()
	info("All attributes and states have been cleared.")
}

//You should initialize your variables. If not, they won't exist until a status change, which could give you off behavior when referencing the device in rules/apps
void initializeVars() {
    trace("In Method : initializeVars()")
    // first run only
    sendEvent(name:"battery", value:100)
    sendEvent(name:"contact", value:"closed")
    sendEvent(name:"numberOfButtons", value:1)
    sendEvent(name:"pushed", value:1)
    sendEvent(name:"tamper", value:"clear")
    state.initialized=true
    info("Variables initialized.")
}

//If you have device configurations being set in the Preferences, you should always run a method (here called runConfigs()) that sets up sending the updated configurations to the device.
void runConfigs() {
    trace("In Method : runConfigs()")
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.addAll(configCmd(param, data.parameterSize, settings[data.input.name]))
        }
    }
    sendToDevice(cmds)
    info("Configs have been sent to device.")
}

//Used by runConfigs() method & can technially be included within it since it isn't called elsewhere. Separated for educational purposes.
List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
    trace("In Method : configCmd(${parameterNumber}, ${size}, ${scaledConfigurationValue})")
    List<hubitat.zwave.Command> cmds = []
    //Note that the 1st command sets the parameter, the 2nd command requests the device sends back the value to the hub so the preferences will then be updated with the value actually on the deviec (so hub & device are always in sync). This is done in the method "zwaveEvent(hubitat.zwave.commands.configurationv4.ConfigurationReport cmd)"
    cmds.add(zwave.configurationV4.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: scaledConfigurationValue.toInteger()).format())
    cmds.add(zwave.configurationV4.configurationGet(parameterNumber: parameterNumber.toInteger()).format())
    return cmds
}

//*************logging methods*************
//Added these methods for ease of enabling/disabling logging. Don't need to always include the IF statement if you reference these methods instead...
void debug(string){
	if (logDebugEnable) {log.debug("${string}")}
}

void trace(string){
	if (logDebugEnable) {log.trace("${string}")}
}

void info(string){ // added solely so if a preference is added in the future to turn these on/off, can add just here for ease
    if (logInfoEnable) {log.info("${string}")}
}

void warn(string){ // added solely so if a preference is added in the future to turn these on/off, can add just here for ease
    log.warn("${string}")
}

void error(string){ // added solely so if a preference is added in the future to turn these on/off, can add just here for ease
    log.error("${string}")
}

//*************Methods to support commands*************
//method that is called when the command "pollDeviceData" is used
void pollDeviceData() {
    trace("In Method : pollDeviceData()")
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV3.versionGet().format())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1).format())
    cmds.add(zwave.batteryV2.batteryGet().format())
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 7, event: 2).format())
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 7, event: 3).format())
    cmds.add(zwave.wakeUpV2.wakeUpIntervalSet(seconds: wakeUpInterval, nodeid:getZwaveHubNodeId()).format())
    cmds.add(zwave.wakeUpV2.wakeUpIntervalGet().format())
    sendToDevice(cmds)
    info("Device polled.")
}

//method that is called when the command "oneTimeShot" is used (note the input from the command is used as the input to the method)
def oneTimeShot(delay){
	cmd = zwave.configurationV4.configurationSet(parameterNumber: 5, size: 2, scaledConfigurationValue: delay).format()
    sendToDevice(cmd)
}

//method that is called when the command "push" is used
void push(){
    Map evt = [name:"pushed", value:1, isStateChange:true, descriptionText:"${device.displayName} Button Pushed"]
    eventProcess(evt)
    Date now = new Date()
    Map evt2 = [name:"buttonPushTime", value:"${now}", isStateChange:true]
    eventProcess(evt2)
}

//Although you don't need a single method to process your events, I did this so I wouldn't have to have multiple Info() methods being called. This 1 method will always send it to the Info() method for me.
void eventProcess(Map evt) {
    trace("In Method : eventProcess(${evt})")
    sendEvent(evt)
    info("Event Processed : ${evt}")
}

//*************Methods to send the device commands*************
//This method is to send multiple commands at once to the device. Defaults to 200ms of delay between commands
void sendToDevice(List<String> cmds, Long delay=200) {
    trace("In Method : sendToDevice(List<String> ${cmds})")
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds, delay), hubitat.device.Protocol.ZWAVE))
    debug("List of commands sent.")
}

//This method is to send 1 command to the device
void sendToDevice(String cmd) { 
    trace("In Method : sendToDevice(String '${cmd}')") 
	def commandClassHex = cmd.substring(0, 2)
	def commandClassHexInt = Integer.parseInt(commandClassHex, 16)
	if (UNSECURE_CLASSES.contains(commandClassHexInt)){
        sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
		debug("Unsecure Command Sent: ${cmd}")
	} else {
        sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd), hubitat.device.Protocol.ZWAVE))
		debug("Secure Command Sent: ${cmd} -> ${zwaveSecureEncap(cmd)}")
	}
}

//This helps the multi-commands method & can technially be included within it since it isn't called elsewhere. Separated for educational purposes.
List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) {
    trace("In Method : commands(List ${cmds})")
	delayedCmds = delayBetween(cmds.collect { cmd -> 
        def command = cmd.toString() //just in case
        if (cmd.substring(0, Math.min(command.length(), 5)) == "delay"){
            cmd
        } else {
            def commandClassHex = command.substring(0, 2)
            def commandClassHexInt = Integer.parseInt(commandClassHex, 16)
            if (UNSECURE_CLASSES.contains(commandClassHexInt)) { 
                debug("Unsecure Command: ${cmd}")
                command
            } else { 
                debug("Secure Command: ${cmd} -> ${zwaveSecureEncap(cmd)}")
                zwaveSecureEncap(command) 
            }
        }
	}, delay)
	debug("Full list of commands : ${delayedCmds}")
	return delayedCmds
}

//*************Methods to receive/respond to device messages*************
//Note all device inputs flow into the parse() function 1st... zwaveEvent(cmd) then sends the command to the proper method [zwaveEvent(objectClass cmd)] based on the object class of 'cmd'
void parse(String description) {
    trace("In Method : parse(${description})")
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
    if (cmd) {
        debug("Parsed Command Object Class : ${getObjectClassName(cmd)}")
		debug("Parsed Command : ${cmd}")
        zwaveEvent(cmd)
    }
}

//below are the various methods required... the device sends it to Parse, and Parse sends it to one of the below methods based on the command's object class.
//If the command class of the object uses security, it will likely come in a 'SupervisionGet'. Each 'SupervisionGet' must have a 'SupervisionReport' sent back to the device or else it might lock up. Many devices have configurable timeouts & retry amounts.
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet)")
    debug("cmd : ${cmd}")
    debug("Supervision Get - SessionID: ${cmd.sessionID}, CC: ${cmd.commandClassIdentifier}, Command: ${cmd.commandIdentifier}")
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        debug("Encapsulated Command :${encapsulatedCommand}")
        debug("Encapsulated Command's Object Class : ${getObjectClassName(encapsulatedCommand)}")
        zwaveEvent(encapsulatedCommand)
    }
    supervisionReportFormatted = zwave.supervisionV1.supervisionReport(
        duration: 0x00,
		moreStatusUpdates: false,
		sessionID: cmd.sessionID,         
        status: 0xFF // SUCCESS is 255 or 0xFF
    	).format()
    // Resolution to a Product Issue : Delay is needed or else the device shows red light... the supervisionReport can't be sent back too quickly
    debug("Sending supervisionReport reply to supervisionGet in approx. ${supervisoryReportDelay}ms") 
    //runInMillis(supervisoryReportDelay,"sendToDevice(", [data: supervisionReportFormatted, misfire: "ignore"]) // Method 1 : This makes a scheduled execution of the method
    sendToDevice(["delay ${supervisoryReportDelay}",supervisionReportFormatted],0) // Method 2 : This adds the delay directly into the HubMultiAction. HubMultiAction takes a list of strings as an input, a string of "delay x" will delay set a delay 'x' milliseconds. It is serially executed, so the next command will NOT be sent until the delay is finished. You can put in as many delays as you want, all actions are done sequentially.
}

//This method is a catch-all in case no other zwaveEvent(...) method accepts the cmd's object class
void zwaveEvent(cmd) {
	trace("In Method : zwaveEvent(cmd)")
	error("Skipped command :${cmd}")
	error("Skipped Object Class : ${getObjectClassName(cmd)}")
}

//This method is a catch-all in case no other zwaveEvent(...) method accepts the cmd's command class's object class
void zwaveEvent(hubitat.zwave.Command cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.Command)")
    error("Skipped command :${cmd}")
	error("Skipped Object Class : ${getObjectClassName(cmd)}")
}

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd) { //removed the code for notification types not applicable to the Ring Alarm Contact Sensor G2
    trace("In Method : zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport)")
    Map evt = [isStateChange:false]
    debug("Notification Type (raw) : ${cmd.notificationType}. Notification Type (readable) : " + ZWAVE_NOTIFICATION_TYPES[cmd.notificationType.toInteger()])
    if (cmd.notificationType==0x07) {
        // home security
        switch (cmd.event) {
            case 0x00:
                // state idle
                if (cmd.eventParametersLength>0) {
                    switch (cmd.eventParameter[0]) {
                        case 0x02:
                            evt.name="contact"
                            evt.value="closed"
                            evt.isStateChange=true
                            evt.descriptionText="${device.displayName} contact became ${evt.value}"
                            break
						case 0x03:
                            evt.name="tamper"
                            evt.value="clear"
                            evt.isStateChange=true
                            evt.descriptionText="${device.displayName} tamper cleared. Cover installed."
                        	break
                        case 0x0B:
                        	warn("${device.displayName} : magnetic field interference cleared")
                        	break
                        default :
                        	warn("${device.displayName} : Notification Type 7, Event 0x00, but parameter (${cmd.eventParameter[0]}) isn't programmed into Device Driver code")
                        	break
                    }
                } else {
                    // should probably do something here
                    warn("${device.displayName} : Notification Type 7, Event 0x00, but no parameter provided")                      	
                }
                break
            case 0x02:
                // Intrusion
                evt.name="contact"
                evt.value="open"
                evt.isStateChange=true
                evt.descriptionText="${device.displayName} contact became ${evt.value}"
                break
            case 0x03:
                // Tampering cover removed
                evt.name="tamper"
                evt.value="detected"
                evt.isStateChange=true
                evt.descriptionText="${device.displayName} tamper alert cover removed"
                break
            case 0x0B:
                // magnetic field interference detected
              	warn("${device.displayName} : magnetic field interference detected")
                break
            default :
            	warn("${device.displayName} : Notification Type 7, but event (${cmd.event}) isn't programmed into Device Driver code")
                break
        }
    } else if (cmd.notificationType==8) {
        // power management
        switch (cmd.event) {
            case 0x01:
                // Power has been applied
                info("${device.displayName} Power has been applied")
                break
            case 0x05:
                // voltage drop / drift
            	warn("${device.displayName} : Voltage Drop/Drift")
                break
            default :
            	warn("${device.displayName} : Notification Type 8, but event (${cmd.event}) isn't programmed into Device Driver code")
                break
        }
    } else if (cmd.notificationType==9) {
        //system
        switch (cmd.event) {
            case 0x05 :
            	push()
            	break
            case 0x04 :
            	if (cmd.eventParametersLength>0) {
                    switch (cmd.eventParameter[0]) {
                        case 0x55:
                            error("${device.displayName} : System Software Failure")
                            break
                        case 0xAA:
                        	error("${device.displayName} : Software Fault (Ring)")
                            break
                        case 0xA9:
                        	error("${device.displayName} : Software Fault (SDK)")
                            break
                        case 0xAB:
                        	warn("${device.displayName} : Pin Reset (soft reset)")
                            break
                        case 0xAC:
                        	warn("${device.displayName} : Software Reset (Not triggered by failure)")
                            break
                        case 0xAD:
                        	warn("${device.displayName} : Dropped Frame")
                            break
                    }
                } else {
                    // should probably do something here
                }
            	break
            default :
            	warn("${device.displayName} : Notification Type 9, but event (${cmd.event}) isn't programmed into Device Driver code")
                break
        	}
    }
    if (evt.isStateChange) {
        eventProcess(evt)
    }
}
                                      
void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport)")
    state.wakeInterval=cmd.seconds
    info("Wake Interval = ${cmd.seconds}")
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification)")
    info("${device.displayName} : Device wakeup notification")
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 7, event: 2).format())
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 7, event: 3).format())
    sendToDevice(cmds)    
}

void zwaveEvent(hubitat.zwave.commands.configurationv4.ConfigurationReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.configurationv4.ConfigurationReport)")
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam=configParams[cmd.parameterNumber.toInteger()]
        int scaledValue
        cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    }
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation)")
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void zwaveEvent(hubitat.zwave.commands.batteryv2.BatteryReport cmd) {
    trace("In Method : zwaveEvent(hhubitat.zwave.commands.batteryv2.BatteryReport)")
    Map evt = [name: "battery", unit: "%"]
    if (cmd.batteryLevel == 0xFF) {
        evt.descriptionText = "${device.displayName} has a low battery"
        evt.value = "1"
    } else {
        evt.descriptionText = "${device.displayName} battery is ${cmd.batteryLevel}%"
        evt.value = "${cmd.batteryLevel}"
    }
    eventProcess(evt)
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport)")
    info("Device Specific Report : ${cmd}")
    switch (cmd.deviceIdType) {
        case 1:
            // serial number
            def serialNumber=""
            if (cmd.deviceIdDataFormat==1) {
                cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff,1).padLeft(2, '0')}
            } else {
                cmd.deviceIdData.each { serialNumber += (char) it }
            }
            device.updateDataValue("serialNumber", serialNumber)
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport)")
    device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    //protocolVersion refers to the Z-Wave SDK being used. Sadly, it doesn't have the patch by number... you'll get 7.17, but not 7.17.2
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
    info("Version Report : ${cmd}")
}

void zwaveEvent(hubitat.zwave.commands.zipnamingv1.ZipNamingNameReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.zipnamingv1.ZipNamingNameReport)")
    name = cmd.name
    device.setLabel("${name}")
    info("Device Label set to : ${name}")
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalCapabilitiesReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalCapabilitiesReport)")
    info("Wake Up Interval Capabilities Report : ${cmd}")
}
