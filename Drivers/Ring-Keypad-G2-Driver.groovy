/*
// Version :		0.1.0
// Version date : 	23 Jan 2025
//
// GitHub Url : 	https://github.com/mikenyc88/Hubitat/
// Author Profile :	https://community.hubitat.com/u/mikenyc/summary
// Community Docs :	TBD
// Donation Url :	https://account.venmo.com/u/Mike--Dam
//
// License URL : 	https://github.com/mikenyc88/Hubitat/blob/main/LICENSE
// License Type :	GNU Affero General Public License v3.0
// License Desc. :	Permissions of this strongest copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights. When a modified version is used to provide a service over a network, the complete source code of the modified version must be made available. (See link or published license material for full details)
//
// Release notes :
		0.1.0  : Initial BETA Release. Built on top of the great work by Jeremy Kister (jkister), Bryan Copeland (@bcopeland), and Bryan Turcotte (@bptworld).
*/

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.time.TimeCategory

def version() {
    return "1.2.12" // MikeNYC - rolled Version number // "1.2.11"
}

def modes = location.getModes().collect { it.name }

metadata {
    definition (name: "Ring Alarm Keypad G2 - Advanced Driver", namespace: "MikeNYC88", author: "Mike Dam", importUrl: "https://github.com/mikenyc88/Hubitat/blob/main/Drivers/Ring-Keypad-G2-Driver.groovy") { //Added MikeNYC Edit to name
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "SecurityKeypad"
        capability "Battery"
        capability "Alarm"
        capability "PowerSource"
        capability "LockCodes"
        capability "Motion Sensor"
        capability "PushableButton"
        capability "HoldableButton"
        capability "Chime"

		command "exitDelay", [[name: "Delay", type: "NUMBER"],[name: "Note", type: "", description: "This command is here to enable use within a Rule. When using in a rule, send 1 'Number' parameter to signify the delay in seconds. If delay is sent, that will become the new default delay. If no delay is sent, default delay is used (see state 'KeypadConfig'). HSM uses different delays depending on type of arming."]]
		command "entry", [[name: "Delay", type: "NUMBER"],[name: "Note", type: "", description: "This command is here to enable use within a Rule. When using in a rule, send 1 'Number' parameter to signify the delay in seconds. If delay is sent, that will become the new default delay. If no delay is sent, default delay is used (see state 'KeypadConfig'). If triggered by HSM, it uses the HSM dictated delay."]]
        command "playSound", [[name: "Play Tone", type: "NUMBER", description: "1..9"]]
        command "resetKeypad", [[name: "clearLockCodes", type: "ENUM", constraints : ["Reset all attributes","Do not reset Lock Codes"], description: "Note : This resets all the keypad attributes/states back to the default (Doesn't affect preferences). Would you like to erase Lock Codes as well? Default : 'Erase all attributes'"]]
        command "syncKeypad", [[name: "syncTo", type: "ENUM", constraints : ["HSM","Mode"], description: "HSM or Mode?"],[name: "Note", type: "", description: "Sync this keypad to either HSM or Mode. This can help re-sync the keypad in the case of odd behaviour/malfunctioning. If this doesn't work, use the resetKeypad command. No input needed."]]
		command "constructMakerApiHsmUrl",[[name:"Note",type:"",description:"This is used to set the URL (in preferences) needed to pull the HSM Status when you are running HSM on another HSM, sharing this keypad through Hub Mesh, and want to force keypad status thru HSM. Not needed otherwise. Refresh screen after running this command to load updated preferences."],[name: "IP Address", type:"STRING",description: "IP address of Hub running HSM"],[name:"appID", type:"NUMBER", description:"MakerAPI's App ID on Hub running HSM"],[name:"accessToken",type:"STRING",description:"MakerAPI's Access Token on Hub running HSM."]]
		
        attribute "alarmStatusChangeTime", "STRING"
        attribute "alarmStatusChangeEpochms", "NUMBER"
        attribute "armingIn", "NUMBER"
		attribute "lastCode", "STRING"
        attribute "lastCodeName", "STRING"
        attribute "lastCodeTime", "STRING"
        attribute "lastCodeEpochms", "NUMBER"
        attribute "motion", "STRING"
        attribute "validCode", "ENUM", ["true", "false"]
        attribute "volAnnouncement", "NUMBER"
        attribute "volKeytone", "NUMBER"
        attribute "volSiren", "NUMBER"

        fingerprint mfr:"0346", prod:"0101", deviceId:["0301","0401"], inClusters:"0x5E,0x98,0x9F,0x6C,0x55", deviceJoinName: "Ring Alarm Keypad G2"
        //inClusters: "0x5E,0x98,0x9F,0x6C,0x55", Secure In Clusters: "0x59,0x85,0x80,0x70,0x5A,0x6F,0x7A,0x87,0x72,0x8E,0x71,0x73,0x86"

    }
    preferences {
        input name: "about", type: "paragraph", element: "paragraph", title: "Ring Alarm Keypad G2 Community Driver", description: "${version()}<br>Note:<br>The first 4 Tones are alarm sounds that also flash the Red Indicator Bar on the keypads. The rest are more pleasant sounds that could be used for a variety of things."
		input name: "triggerNote", type: "paragraph", element: "paragraph", title: "Note: Arming/Disarming Virtually", description: "You can control the arming/disarming of the keypad virtually via Device Details (here), HSM (if selected in HSM's options), and Rules (via 'run custom action'). When using in a rule, send 1 'Number' parameter to signify the delay in seconds. If no delay is sent, default delay is used (see state 'KeypadConfig'). If triggered by HSM, it uses the HSM dictated delay."
        input name: "emergencyButtonsNote", type: "paragraph", element: "paragraph", title: "Note: Using Emergency Buttons", description: "You can use the Police, Fire, and Medical buttons on the keypad. This is possible by subscribing to the change in the attributes of 'held' (will show '11' for police, '12' for fire, '13' for medical) or 'lastCodeName' (will say 'police', 'fire', or 'medical')"
		input name: "customRuleByCodeNote", type: "paragraph", element: "paragraph", title: "Note: Activating Rules by Code", description: "You can use the keypad to activate Rules without needing to save PINs/Codes. Turn on the preference 'Save the last code used?' and have your rule subscribe (trigger) to the custom attribute 'lastCode'."
		configParams.each { input it.value.input }
        input name: "theTone", type: "enum", title: "Chime/tone", description: "This will be used when no input is made for the Chime/playSound command. Tones 1-4 have Strobe.", options: [
            [1:"Tone 1: Siren"],
            [2:"Tone 2: Siren (+ badge button lit)"],
			[3:"Tone 3: 3 long Beeps (Smoke/Fire)"],
            [4:"Tone 4: 4 short beeps (Carbon Monoxide)"],
            [5:"Tone 5: Navi"],
            [6:"Tone 6: Guitar"],
            [7:"Tone 7: Windchimes"],
            [8:"Tone 8: DoorBell 1"],
            [9:"Tone 9: DoorBell 2"],
            [10:"Tone 10: Sensor Bypass"],
			[11:"Tone 11: Invalid Code Sound"]
        ], defaultValue: 1
        input name: "disarmAll", type: "bool", title: "Change Disarm into Disarm All?", defaultValue: false, description: "Turning this ON ignores any setting in HSM."
        input name: "instantArming", type: "bool", title: "Enable set alarm without code", defaultValue: false, description: ""
        input name: "requireDisarmInPartial", type: "bool", title: "Require disarming to leave HSM's partial-arm modes (home/night)?", defaultValue: true, description: "Turning this OFF will allow you to skip the disarm step when in a Partial-arm mode and start the Exit Delay for HSM's Partial-arm and Away modes." //MikeNYC88 - added this
        input name: "validateCheck", type: "bool", title: "Validate codes submitted with checkmark", defaultValue: false, description: ""
        input name: "forceStateChangeThru", type: "enum", title: "Force keypad's state thru Mode/HSM/App/Rule?", defaultValue: "HSM", options: ["HSM","Mode","App/Rule","N/A"], description: "<b>HSM</b> workflow : Button -> HSM -> Device checks HSM -> State Change... <b>Mode</b> workflow : Button -> Mode Change -> Rule triggers device -> Device checks Mode -> State Change... <b>App/Rule</b> workflow : Button -> 'lastCode' attribute (ensure turned on) -> Rule -> Device confirms driven by app -> state change... <b>N/A</b> workflow : Button -> state change... <b>Note 1</b> : It is recommended you use this function if you use with HSM with sensors that are not bypassed (must be closed to arm). <b>Note 2</b> : If selecting 'Mode', ensure Mode changes before triggering the keypad & you select the modes you want to use for each arm state in the respective preference on this page."
		if(forceStateChangeThru =="HSM"){
			input name: "makerApiHsmUrl", type: "string", title: "Using 'Force HSM' & Hub Mesh? Enter Maker API URL for HSM", defaultValue: "", description: "If you are using Hub Mesh, forcing state thru HSM, and have HSM running on a different hub than this keypad, then you you need to enter the URL to pull the hsmStatus using MakerAPI from the hub running HSM. If you don't know how to do it, use the command that appeared in the commands menu of Device Details. <b>Note</b> Hub running HSM should have a status IP from your router so URL doesn't change."
		}
		input name: "syncButton", type: "enum", title: "Button to re-sync with HSM/Mode", description: "Re-syncing the keypad can help in the case of odd behaviour/malfunctioning. If this doesn't work, use the resetKeypad command. <b>Note</b> If keypad state is forced thru HSM or Mode, this will sync with where it is forced. If not, a new preference will appear after you <b>SAVE</b> preferences with a different 'Force keypad's state thru...' preference. ", defaultValue: "X button", options: ["N/A","X button","Checkmark button"] //MikeNYC88 - added this
        input name: "buttonSyncsTo", type: "enum", title: "Resync to HSM or Mode?", description: "By hitting the resync button, what do you want to resync with? <B>Note :</b> If forcing keypad state thru HSM or Mode, this must match.", defaultValue: "HSM", options: ["HSM","Mode","Don't Sync"] //MikeNYC88 - added this
		input name: "partialFunctionValue", type: "enum", title: "Home Button Action", options: [
            ["armHome":"Arm Home (default)"],
            ["armNight":"Arm Night"],
        ], defaultValue: "armHome", description: "After setting this, press \"Save Preferences\""// below then press the \"Set Partial Function\" in commands"
        input name: "disableProximitySensor", type: "bool", title: "Disable the Proximity Sensor?", defaultValue: true, description: "Keypad will likely not work with Proximitity Sensor enabled. It is highly recommended you keep the sensor disabled." //MikeNYC made this default to true
        input name: "volAnnouncement", type:"number", title: "Announcement Volume", description: "Volume level (1-10)", defaultValue: 7, range: "0..10"
        input name: "volKeytone", type:"number", title: "Keytone Volume", description: "Volume level (1-10)", defaultValue: 6, range: "0..10"
        input name: "volSiren", type:"number", title: "Siren Volume", description: "Volume level (1-10)", defaultValue: 10, range: "0..10"
		input name: "armAwayDelay", type:"number", title: "Arm Away Delay", description: "Delay in Seconds", defaultValue: 5, range:"0..*"
        input name: "armNightDelay", type:"number", title: "Arm Night Delay", description: "Delay in Seconds", defaultValue: 5, range:"0..*"
        input name: "armHomeDelay", type:"number", title: "Arm Home Delay", description: "Delay in Seconds", defaultValue: 5, range:"0..*"
        input name: "saveLastCode", type: "bool", title: "Save the last code used?", defaultValue: false, description: "This will store the last code used for any arming button or the checkmark in the state \"lastCode\" for 30 seconds (deleted afterward for security purposes). Note, they will still visible in Event History."
		input name: "removeCodeFromLogs", type: "bool", title: "Remove PINs/Codes from log entries?", defaultValue: false, description: "Enable this to remove PINs/Codes from log entries."
		input name: "optEncrypt", type: "bool", title: "Enable lockCode encryption", defaultValue: false, description: ""
		input name: "disarmMode", type: "enum", title: "Trigger Mode - Disarm", options: location.getModes().collect {it.name}+ ["N/A"], description: "What mode do you want to trigger on Keypad Disarm?"
		input name: "partialMode", type: "enum", title: "Trigger Mode - Partial (home/night)", options: location.getModes().collect {it.name}+ ["N/A"], description: "What mode do you want to trigger on Keypad Arm Partial (Home/Night)?"
		input name: "awayMode", type: "enum", title: "Trigger Mode - Away", options: location.getModes().collect {it.name}+ ["N/A"], description: "What mode do you want to trigger on Keypad Arm Away?"
		input name: "logDebugEnable", type: "bool", title: "Enable debug/trace logging", defaultValue: false, description: "Turn this off when you don't need it. This drastically slows keypad operation due to the numerous log entries."
		input name: "logInfoEnable", type: "bool", title: "Enable info logging", defaultValue: true, description: "Info includes major actions like Events."
		input name: "logEnableForSetExitDelays", type: "bool", title: "Enable setExitDelay debug logging", description: "Enable debug logging for method 'setExitDelay'? HSM rapid fires this 1/sec during any delay, which could crowd your log history. Note: Logging must be enabled as well.", defaultValue: false
    }
}

@Field static Map configParams = [
        //4: [input: [name: "configParam4", type: "enum", title: "Announcement Volume", description:"", defaultValue:7, options:[0:"0",1:"1",2:"2",3:"3",4:"4",5:"5",6:"6",7:"7",8:"8",9:"9",10:"10"]],parameterSize:1],
        //5: [input: [name: "configParam5", type: "enum", title: "Keytone Volume", description:"", defaultValue:6, options:[0:"0",1:"1",2:"2",3:"3",4:"4",5:"5",6:"6",7:"7",8:"8",9:"9",10:"10"]],parameterSize:1],
        //6: [input: [name: "configParam6", type: "enum", title: "Siren Volume", description:"", defaultValue:10, options:[0:"0",1:"1",2:"2",3:"3",4:"4",5:"5",6:"6",7:"7",8:"8",9:"9",10:"10"]],parameterSize:1],
        7: [input: [name: "configParam7", type: "number", title: "Long press Emergency Duration", description:"", defaultValue: 3, range:"2..5"],parameterSize:1],
        8: [input: [name: "configParam8", type: "number", title: "Long press Number pad Duration", description:"", defaultValue: 3, range:"2..5"],parameterSize:1],
        12: [input: [name: "configParam12", type: "number", title: "Security Mode Brightness", description:"", defaultValue: 100, range:"0..100"],parameterSize:1],
        13: [input: [name: "configParam13", type: "number", title: "Key Backlight Brightness", description:"", defaultValue: 100, range:"0..100"],parameterSize:1],
]
@Field static Map armingStates = [
        0x00: [securityKeypadState: "armed night", hsmCmd: "armNight"],
        0x02: [securityKeypadState: "disarmed", hsmCmd: "disarm"],
        0x0A: [securityKeypadState: "armed home", hsmCmd: "armHome"],
        0x0B: [securityKeypadState: "armed away", hsmCmd: "armAway"]
]

@Field static List UNSECURE_CLASSES=[
        0x5E, // Z-Wave Plus Info V2
        0x98, // Security S0
        0x9F, // Security S2
        0x6C, // Supervision
        0x55  // Transport Service V2
    ]

@Field static Map CMD_CLASS_VERS=[        
		0x59 : 3, // Association Group Information V3
        0x85 : 2, // Association V2
        0x80 : 2, // Battery V2 (Tech Manual says V2, Zwave alliance says V1)
        0x70 : 4, // Configuration V4
        0x5A : 1, // Device Reset Locally
        0x6F : 1, // Entry Control
        0x7A : 5, // Firmware Update Meta-Data V5
        0x87 : 3, // Indicator V3
        0x72 : 2, // Manufacturer Specific V2
        0x8E : 3, // Multi-Channel Association V3
        0x71 : 8, // Notification V8
        0x73 : 1, // Powerlevel
        0x98 : 1, // Security S0
        0x9F : 1, // Security S2
        0x6C : 1, // Supervision
        0x55 : 2, // Transport Service V2
        0x86 : 3, // Version V3
        0x5E : 2  // Z-Wave Plus Info V2
    ]

// Methods - Mandatory
void uninstalled() {
	trace("In Method : uninstalled()")
}

void updated() {
	trace("In Method : updated()")
    info("updated...")
    info("debug/trace logging is: ${logDebugEnable}")
	info("info logging is: ${logInfoEnable}") 
    info("encryption is: ${optEncrypt}")
    unschedule()
    sendToDevice(runConfigs())
    updateEncryption()
    proximitySensorHandler()
    setPartialFunction()
    setVolumeSettings()
	setExitDelay()
	if(syncButton == "Checkmark button" && validateCheck == true){
		device.updateSetting("syncButton", [value: "X button", type: "string"])
		warn("syncButton can't be checkmark when validateCheck is true. Changing syncButton to 'X button'")
	}
    if(forceStateChangeThru =="HSM"){
        device.updateSetting("syncTo", [value: "HSM", type: "string"])
        warn("Syncing must be done with HSM when forcing status thru HSM.")
    } else if (forceStateChangeThru == "Mode"){
        device.updateSetting("syncTo", [value: "Mode", type: "string"])
        warn("Syncing must be done with Mode when forcing status thru Mode (via app).")
    }
}

void installed() {
	proximitySensorHandler(true) //MikeNYC88 added
    initializeVars()
}

// Methods - setup, reset, polling
void initializeVars() {
	trace("In Method : initializeVars()")
    // first run & on resetKeypad()
    eventProcess(name:"codeLength", value: 4)
    eventProcess(name:"maxCodes", value: 100)
	if(!lockCodes) {eventProcess(name:"lockCodes", value: "")}
    eventProcess(name:"volAnnouncement", value: 7)
    eventProcess(name:"volKeytone", value: 6)
    eventProcess(name:"volSiren", value: 10) // MikeNYC88 changed from 75 to 10
    eventProcess(name:"securityKeypad", value:"disarmed")
    state.keypadConfig=[entryDelay:5, exitDelay: 5, armNightDelay:5, armAwayDelay:5, armHomeDelay: 5, codeLength: 4, partialFunction: "armHome"]
    state.keypadStatus=2
    state.initialized=true
}

void resetKeypad(clearLockCodes = "Reset all attributes") {
	trace("In Method : resetKeypad(${clearLockCodes})")
	clearAllAttributesAndStates(clearLockCodes) //MikeNYC88 - clears all states & attributes
    state.initialized=false
	configure()
    getCodes()
}

void clearAllAttributesAndStates(clearLockCodes) {
	trace("In Method : clearAllAttributesAndStates(${clearLockCodes})")
    // Clear all attributes dynamically
	device.currentStates.each { state -> 
		if (state.name != "lockCodes"){	
			device.deleteCurrentState("${state.name}")
		} else if (clearLockCodes != "Do not reset Lock Codes") {
			device.deleteCurrentState("${state.name}")
		}
	}
	// Clear state variables
    state.clear()
	info("All attributes and states have been cleared.")
}

void configure() {
	trace("In Method : configure()")
    if (!state.initialized) initializeVars()
    if (!state.keypadConfig) initializeVars()
    runIn(5,pollDeviceData, [misfire: "ignore"])
	switch (buttonSyncsTo){
		case "HSM" :
        	syncKeypad("HSM")
			break
		case "Mode" :
        	syncKeypad("Mode")
			break
		default :
			keypadUpdateStatus(state.keypadStatus, state.type, state.code)
			break
	}
}

void pollDeviceData() {
	trace("In Method : pollDeviceData()")
    List<String> cmds = []
    cmds.add(zwave.versionV3.versionGet().format())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1).format())
    cmds.add(zwave.batteryV1.batteryGet().format())
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 8, event: 0).format())
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 7, event: 0).format())
    cmds.addAll(processAssociations())
    sendToDevice(cmds)
}

//logging methods
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

//Methods - Syncing and update status/events

def confirmAppDriven(data) { //This just makes sure it was driven by an app (could be a rule or HSM) & not a button press in Device Details
	trace("In Method : confirmAppDriven(${data})")
	def command = data.command
	def delay = data.delay
	def commandEvent
	use(TimeCategory) {
    	def events = device.eventsSince(new Date() - 30.seconds)
	}
	if (command) {
		if (command == "armNight") {
			commandEvent = events.find { it.name == "command-armNight"}
		} else if (command == "armAway") {
			commandEvent = events.find { it.name == "command-armAway"}
		} else if (command == "armHome") {
			commandEvent = events.find { it.name == "command-armHome"}
		} else if (command == "disarm") {
			commandEvent = events.find { it.name == "command-disarm"}
		}
	}
	def source = commandEvent.producedBy.substring(0, 3)
	if (source == "APP"){
		debug("In confirmAppDriven : Passed. Starting ${command}Start")
		switch (command) {
			case armNight : 
				armNightStart(delay)
				break
			case armAway : 
				armAwayStart(delay)
				break
			case armHome : 
				armHomeStart(delay)
				break
			case disarm : 
				disarmStart(delay)
				break
		}
	} else {
		debug("Command was not driven by an App.")
	}
}

def syncKeypad(syncTo, note = "from command, isn't used") {
    trace("In Method : syncKeypad(${syncTo})")
    state.code = ""
    state.type = "physical"
    switch (syncTo) {
        case "HSM" : 
        	    def hsmState
                if (makerApiHsmUrl) {
                    hsmState = pullExternalHubHsmStatus()
                } else {
                    hsmState = location.hsmStatus
                }
                debug("Syncing Keypad to HSM, hsmStatus : ${hsmState}")
                switch (hsmState){
                    case ["armedAway", "armingAway"]:
                        keypadUpdateStatus(0x0B, state.type, state.code)
                        if (hsmState == armingAway) {
                            warn("Synced keypad to HSM state : HSM was mid-delay in armingAway, but Keypad was set to armedAway with no delay.")
                        }
                    break
                    case ["armedHome", "armingHome"]:
                        keypadUpdateStatus(0x0A, state.type, state.code)
                        if (hsmState == armingHome) {
                            warn("Synced keypad to HSM state : HSM was mid-delay in armingHome, but Keypad was set to armedHome with no delay.")
                        }
                    break
                    case ["armedNight", "armingNight"]:
                        keypadUpdateStatus(0x00, state.type, state.code)
                        if (hsmState == armingNight) {
                            warn("Synced keypad to HSM state : HSM was mid-delay in armingNight, but Keypad was set to armedNight with no delay.")
                        }
                    break
                    case "disarmed":
                        keypadUpdateStatus(0x02, state.type, state.code)
                    break
                    case "allDisarmed":
                        keypadUpdateStatus(0x02, state.type, state.code)
                    break
                    default : //for armingAway, armingNight, armingHome
                        error("Failed to sync keypad to HSM state since HSM state is non-standard : ${hsmState}")
                    break
                }
        	break
        case "Mode" :
            def locMode = location.getMode().toString()
            debug("Syncing Keypad to Mode, location.getMode() : ${locMode}")
            switch (locMode){
                case partialMode :
                    keypadUpdateStatus(0x0A, state.type, state.code)
                    break
                case disarmMode :
                    keypadUpdateStatus(0x02, state.type, state.code)
                    break
                case awayMode :
                    keypadUpdateStatus(0x0B, state.type, state.code)
                    break
                default :
                    error("Failed to sync keypad to Mode state since Mode state isn't associated with a Keypad State in preferences. Current Mode: ${locMode}. Disarmed Mode: ${disarmMode}. Partial Mode: ${partialMode}. Away Mode: ${awayMode}")
                    break
            }
        	break
        default :
        	break
    }
}

def checkAgainstMode(data) {
	trace("In Method : checkAgainstMode(${data})")
	def command = data.command
	def delay = data.delay
	if (command) {
		def locMode = location.getMode().toString()
		if ((command == "armNight" || command == "armHome") && locMode != partialMode) {
			location.setMode("${partialMode}")
		} else if (command == "armAway" && locMode != awayMode) {
			location.setMode("${awayMode}")
		} else if (command == "disarm" && locMode != disarmMode) {
			location.setMode("${disarmMode}")
		} else {
			debug("In checkAgainstMode : Passed. Starting ${command}Start")
			switch (command) {
				case armNight : 
					armNightStart(delay)
					break
				case armAway : 
					armAwayStart(delay)
					break
				case armHome : 
					armHomeStart(delay)
					break
				case disarm : 
					disarmStart(delay)
					break
			}
		}
	}
}
	
def checkAgainstHSM(data) { // added by MikeNYC88
	trace("In Method : checkAgainstHSM(${data})")
	def command = data.command
	def delay = data.delay
	if (command) {
		def hsmState
		if (makerApiHsmUrl) {
			hsmState = pullExternalHubHsmStatus()
			debug("External Hub's hsmStatus to check against : ${hsmState}")
		} else {
			hsmState = location.hsmStatus
			debug("Hub's hsmStatus to check against : ${hsmState}")
		}
		if (command == "armNight" && hsmState != "armedNight" && hsmState != "armingNight") {
			eventProcess(name:"armingIn", value: "armNight", data:[armCmd: "armNight"], isStateChange:true)
		} else if (command == "armAway" && hsmState != "armedAway" && hsmState != "armingAway") {
			eventProcess(name:"armingIn", value: "armAway", data:[armCmd: "armAway"], isStateChange:true)
		} else if (command == "armHome" && hsmState != "armedHome" && hsmState != "armingHome") {
			eventProcess(name:"armingIn", value: "armHome", data:[armCmd: "armHome"], isStateChange:true)
		} else if (command == "disarm" && hsmState != "disarmed" && hsmState != "allDisarmed") {
			if (disarmAll) {
				eventProcess(name:"armingIn", value: "disarmAll", data:[armCmd: "disarmAll"], isStateChange:true)
			} else {
				eventProcess(name:"armingIn", value: "disarm", data:[armCmd: "disarm"], isStateChange:true)
			}
		} else {
			debug("In checkAgainstHSM : Passed. Starting ${command}Start")
			switch (command) {
				case armNight : 
					armNightStart(delay)
					break
				case armAway : 
					armAwayStart(delay)
					break
				case armHome : 
					armHomeStart(delay)
					break
				case disarm : 
					disarmStart(delay)
					break
			}
		}
	}
}

void keypadUpdateStatus(Integer status,String type="digital", String code) {
	trace("In Method : keypadUpdateStatus(${status}, ${type}, ${code})")
    // put keypad into Home mode when system is in Night mode
    int kpstatus = status
	if (kpstatus == 0x00) {kpstatus = 0x0A} //{kpstatus = 0x0A}

    sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:kpstatus, propertyId:2, value:0xFF]]).format())
	state.keypadStatus = kpstatus //MikeNYC88 changed this to be 'kpstatus' instead of 'status'
	debug("In Method : keypadUpdateStatus. kpstatus (${kpstatus}) was sent to keypad & driver's state.keypadStatus") 

	if (state.code != "") { type = "physical" }
    eventProcess(name: "securityKeypad", value: armingStates[status].securityKeypadState, type: type, data: state.code)
    
	state.code = ""
    state.type = "digital"
}

def changeStatus(data) {
	trace("In Method : changeStatus(${data})")
    eventProcess(name: "alarm", value: data, isStateChange: true)
}

void handleButtons(String code) {
	trace("In Method : handleButtons(${code})")
    List<String> buttons = code.split('')
    for (String btn : buttons) {
        try {
            int val = Integer.parseInt(btn)
            eventProcess(name: "pushed", value: val, isStateChange: true)
        } catch (NumberFormatException e) {
            // Handle button holds here
            char ch = btn
            char a = 'A'
            int pos = ch - a + 1
            eventProcess(name: "held", value: pos, isStateChange: true)
        }
    }
}

void eventProcess(Map evt) {
	trace("In Method : eventProcess(${evt})")
	sendEvent(evt)
	info("Event - ${evt}") //All events should have logs
}

//Methods - Arming Logic
void parseEntryControl(Short command, List<Short> commandBytes) {
	trace("In Method : parseEntryControl(${command}, ${commandBytes})")
    if (command == 0x01) {
        Map ecn = [:]
        ecn.sequenceNumber = commandBytes[0]
        ecn.dataType = commandBytes[1]
        ecn.eventType = commandBytes[2]
        ecn.eventDataLength = commandBytes[3]
        def currentStatus = device.currentValue('securityKeypad')
        def alarmStatus = device.currentValue('alarm')
        String code=""
        if (ecn.eventDataLength>0) {
            for (int i in 4..(ecn.eventDataLength+3)) {
                if (removeCodeFromLogs == false) debug("character ${i}, value ${commandBytes[i]}") //MikeNYC88 added code to remove the PIN/Code as per the preference setting
                code += (char) commandBytes[i]
            }
        }
		debug("Entry control: ${ecn}${if(removeCodeFromLogs == false){" keycache: ${code}"}else{""}}")//MikeNYC88 added code to remove the PIN/Code as per the preference setting
        switch (ecn.eventType) {
            case 5:    // Away Mode Button
                debug("In case 5 - Away Mode Button")
                if (validatePin(code) || instantArming) {
                    if(currentStatus == "disarmed" || (requireDisarmInPartial == false && (currentStatus == "armed night" || currentStatus == "armed home"))) {
                        debug("In case 5 (armAway via button) - Passed - currentStatus: ${currentStatus}")
                        state.type="physical"
                        if (!state.keypadConfig.armAwayDelay) { state.keypadConfig.armAwayDelay = 0 }
                        if(requireDisarmInPartial == false && (currentStatus == "armed night" || currentStatus == "armed home")){
                            eventProcess(name: "securityKeypad", value: "re-arm during partial") //MikeNYC added this
                            eventProcess(name:"armingIn", value: "disarm", data:[armCmd: "disarm"], isStateChange:true)
                            runInMillis(1,"awayButton", [misfire: "ignore"]) // delay is needed to give HSM time to disarm and not overlap with the new arming
                        } else {
                            awayButton()
                        }
                    } else {
                        warn("In case 5 (armAway via button) - Failed - Please Disarm Alarm before changing alarm type - currentStatus: ${currentStatus}")
                    }
                } else {
                    warn("In case 5 (armAway via button) - Failed - Invalid PIN - currentStatus: ${currentStatus}")
                    sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x09, propertyId:2, value:0xFF]]).format())
                }
                break
            case 6:    // Home Mode Button
                debug("In case 6 (armHome via button) - Home Mode Button")
                if (validatePin(code) || instantArming) {
                    if(currentStatus == "disarmed" || (requireDisarmInPartial == false && (currentStatus == "armed night" || currentStatus == "armed home"))) {
                        debug("In case 6 (armHome via button) - Passed")
                        state.type="physical"
                        if(!state.keypadConfig.partialFunction) state.keypadConfig.partialFunction="armHome"
                        if(requireDisarmInPartial == false && (currentStatus == "armed night" || currentStatus == "armed home")){
                            eventProcess(name: "securityKeypad", value: "re-arm during partial") //MikeNYC added this
                            eventProcess(name:"armingIn", value: "disarm", data:[armCmd: "disarm"], isStateChange:true)
                            runInMillis(1,"partialButton", [misfire: "ignore"]) // delay is needed to give HSM time to disarm and not overlap with the new arming
                        } else {
                            partialButton()
                        }
                    } else {
                        /*if(alarmStatus == "active") {
                            debug("In case 6 (armHome via button) - Silenced - Alarm will sound again in 10 seconds - currentStatus: ${currentStatus}")
                            changeStatus("silent")
                            runIn(10, changeStatus, [data:"active", misfire: "ignore"])
                        } else {*/
                            warn("In case 6 (armHome via button) - Failed - Please Disarm Alarm before changing alarm type - currentStatus: ${currentStatus}")
                        //}
                    }
                } else {
                    warn("In case 6 (armHome via button) - Failed - Invalid PIN - currentStatus: ${currentStatus}")
                    sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x09, propertyId:2, value:0xFF]]).format())
                }
                break
            case 3:    // Disarm Mode Button
                debug("In case 3 - Disarm Mode Button")
                if (validatePin(code)) {
                    debug("In case 3 - Code Passed")
                    state.type="physical"
                    if (disarmMode && disarmMode != "N/A") {
						location.setMode("${disarmMode}")
					}
					if (disarmAll == false) {
                        eventProcess(name:"armingIn", value: "disarm", data:[armCmd: "disarm"], isStateChange:true)
                    } else {
                        eventProcess(name:"armingIn", value: "disarmAll", data:[armCmd: "disarmAll"], isStateChange:true)
                    }
					if (forceStateChangeThru == "N/A"){
						disarmStart() //MikeNYC88 - this allows the keypad to not require HSM to change states, which occurs via the "armingIn" event.
					}
                } else {
                    warn("In case 3 (disarm via button) - Disarm Failed - Invalid PIN - currentStatus: ${currentStatus}")
                    sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x09, propertyId:2, value:0xFF]]).format())
                }
                break
            // Added all buttons
            case 2:    // Code sent after hitting the Check Mark
                state.type="physical"
                Date now = new Date()
                long ems = now.getTime()
                if(!code) code = "check mark"
                if (syncButton == "Checkmark button") {
					switch (buttonSyncsTo){
						case "HSM" :
                        	syncKeypad("HSM")
							break
						case "Mode" :
                        	syncKeypad("Mode")
							break
					}
				}
                if (validateCheck) {
                    if (validatePin(code)) {
                        debug("In case 2 (check mark) - Code Passed")
                    } else {
                        warn("In case 2 (check mark) - Code Failed - Invalid PIN - currentStatus: ${currentStatus}")
						sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x09, propertyId:2, value:0xFF]]).format())
                    }
				} else {
					debug("checkmark button pressed, but validateCheck == false.")
				}
                break
            case 17:    // Police Button
                state.type="physical"
                Date now = new Date()
                long ems = now.getTime()
                sendEvent(name:"validCode", value: "false", isStateChange: true)
                eventProcess(name:"lastCodeName", value: "police", isStateChange:true)
                sendEvent(name:"lastCodeTime", value: "${now}", isStateChange:true)
                sendEvent(name:"lastCodeEpochms", value: "${ems}", isStateChange:true)
                sendEvent(name: "held", value: 11, isStateChange: true)
                break
            case 16:    // Fire Button
                state.type="physical"
                Date now = new Date()
                long ems = now.getTime()
                sendEvent(name:"validCode", value: "false", isStateChange: true)
                eventProcess(name:"lastCodeName", value: "fire", isStateChange:true)
                sendEvent(name:"lastCodeTime", value: "${now}", isStateChange:true)
                sendEvent(name:"lastCodeEpochms", value: "${ems}", isStateChange:true)
                sendEvent(name: "held", value: 12, isStateChange: true)
                break
            case 19:    // Medical Button
                state.type="physical"
                Date now = new Date()
                long ems = now.getTime()
                sendEvent(name:"validCode", value: "false", isStateChange: true)
                eventProcess(name:"lastCodeName", value: "medical", isStateChange:true)
                sendEvent(name:"lastCodeTime", value: "${now}", isStateChange:true)
                sendEvent(name:"lastCodeEpochms", value: "${ems}", isStateChange:true)
                sendEvent(name: "held", value: 13, isStateChange: true)
                break
            case 1:     // Button pressed or held, idle timeout reached without explicit submission
                state.type="physical"
                handleButtons(code)
            	break
            case 25: //the 'X' button was pressed - MikeNYC88 added
        		state.type="physical"
                if (syncButton == "X button") {
					switch (buttonSyncsTo){
						case "HSM" :
                        	syncKeypad("HSM")
							break
						case "Mode" :
                        	syncKeypad("Mode")
							break
					}
				}
        		break
    	}
	}
}

def partialButton() {
	trace("In Method : partialButton()")
    if (partialMode && partialMode != "N/A") {
		location.setMode("${partialMode}")
	}
	if (state.keypadConfig.partialFunction == "armHome") {
        debug("In case 6 - armHome Passed")
        if (!state.keypadConfig.armHomeDelay) { state.keypadConfig.armHomeDelay = 0 }
        eventProcess(name:"armingIn", value: "armHome", data:[armCmd: "armHome"], isStateChange:true)
		if (forceStateChangeThru == "N/A"){
			armHomeStart(state?.keypadConfig?.armHomeDelay) //MikeNYC88 - this allows the keypad to not require HSM to change states, which occurs via the "armingIn" event.
		}
    }
    if (state.keypadConfig.partialFunction == "armNight") {
        debug("In case 6 - armNight Passed")
        if (!state.keypadConfig.armNightDelay) { state.keypadConfig.armNightDelay = 0 }
        eventProcess(name:"armingIn", value: "armNight", data:[armCmd: "armNight"], isStateChange:true)
		if (forceStateChangeThru == "N/A"){
			armNightStart(state?.keypadConfig?.armNightDelay) //MikeNYC88 - this allows the keypad to not require HSM to change states, which occurs via the "armingIn" event.
		}
    }
}

def awayButton() {
	trace("In Method : awayButton()")
    if (awayMode && awayMode != "N/A") {
		location.setMode("${awayMode}")
	}
	eventProcess(name:"armingIn", value: "armAway", data:[armCmd: "armAway"], isStateChange:true) // MikeNYC moved this here to change workflow and stop looping
    if (forceStateChangeThru == "N/A"){
        armAwayStart(state.keypadConfig.armAwayDelay) //MikeNYC88 - this allows the keypad to not require HSM to change states, which occurs via the "armingIn" event.
    }
}

void armNight(delay=state?.keypadConfig?.armNightDelay) { //MikeNYC made delay default to state.keypadConfig if null or not provided
    trace("In Method : armNight(${delay})")
	switch (forceStateChangeThru){
		case "HSM" :
			runInMillis (1, "checkAgainstHSM",[data:[command : "armNight", delay : delay], misfire: "ignore"])
			break
		case "Mode" :
			runInMillis (1, "checkAgainstMode",[data:[command : "armNight", delay : delay], misfire: "ignore"])
			break
		case "App/Rule" :
			runInMillis (1, "confirmAppDriven",[data:[command : "armNight", delay : delay], misfire: "ignore"])
			break
		default :
			eventProcess(name:"armingIn", value: "armNight", data:[armCmd: "armNight"])//, isStateChange:true) HSM won't trigger if it isn't a change of state. HSM can trigger this. We can't have looping logic.
			runInMillis (1, "armNightStart",[data: delay, misfire: "ignore"]) //delay gives securityKeypad an opportunity to update & block a subsequent method calling
			break
	}
}

def armNightStart(delay=state?.keypadConfig?.armNightDelay) {
	trace("In Method : armNightStart(${delay})")
	def sk = device.currentValue("securityKeypad")
    if (sk != "armed night" && sk != "arming night") { //MikeNYC added the 2nd arming state to ensure it can't go through the loop again by mistake
        eventProcess(name: "securityKeypad", value: "arming night") //MikeNYC added this to ensure if the HSM finishes 1st and resends the call for this method, that it isn't looped through again.
		if (delay > 0 ) {
			exitDelay(delay)
            runIn(delay, armNightEnd, [misfire: "ignore"])
        } else {
            armNightEnd()
        }
    } else {
        debug("In armNight - securityKeypad already set to 'armed night' or 'arming night', so skipping.") //MikeNYC added verbiage
    }
}

void armNightEnd() {
	trace("In Method : armNightEnd()")
    if (!state.code) { state.code = "" }
    if (!state.type) { state.type = "physical" }
    state.remove("delayEndTime")
	def sk = device.currentValue("securityKeypad")
    if(sk != "armed night") {
        keypadUpdateStatus(0x00, state.type, state.code)

        Date now = new Date()
        long ems = now.getTime()
        sendEvent(name:"alarmStatusChangeTime", value: "${now}", isStateChange:true)
        sendEvent(name:"alarmStatusChangeEpochms", value: "${ems}", isStateChange:true)
    }
}

void armAway(delay=state?.keypadConfig?.armAwayDelay) { //MikeNYC made delay default to state.keypadConfig if null or not provided
    trace("In Method : armAway(${delay})")
	switch (forceStateChangeThru){
		case "HSM" :
			runInMillis (1, "checkAgainstHSM",[data:[command : "armAway", delay : delay], misfire: "ignore"])
			break
		case "Mode" :
			runInMillis (1, "checkAgainstMode",[data:[command : "armAway", delay : delay], misfire: "ignore"])
			break
		case "App/Rule" :
			runInMillis (1, "confirmAppDriven",[data:[command : "armAway", delay : delay], misfire: "ignore"])
			break
		default :
			eventProcess(name:"armingIn", value: "armAway", data:[armCmd: "armAway"])//, isStateChange:true) HSM won't trigger if it isn't a change of state. HSM can trigger this. We can't have looping logic.
			runInMillis (1, "armAwayStart",[data: delay, misfire: "ignore"]) //delay gives securityKeypad an opportunity to update & block a subsequent method calling
			break
	}
}

void armAwayStart(delay=state?.keypadConfig?.armAwayDelay) { //MikeNYC made delay default to state.keypadConfig if null or not provided
    trace("In Method : armAwayStart(${delay})")
    def sk = device.currentValue("securityKeypad")
    if(sk != "armed away" && sk != "arming away") { //MikeNYC added the 2nd arming state to ensure it can't go through the loop again by mistake
        eventProcess(name: "securityKeypad", value: "arming away") //MikeNYC added this to ensure if the HSM finishes 1st and resends the call for this method, that it isn't looped through again.
		if (delay > 0 ) {
			exitDelay(delay)
            runIn(delay, armAwayEnd, [misfire: "ignore"])
		}else{
            armAwayEnd()
        }
    } else {
        debug("In armAway - securityKeypad already set to 'armed away' or 'arming away', so skipping.")
    }
}

void armAwayEnd() {
	trace("In Method : armAwayEnd()")
    if (!state.code) { state.code = "" }
    if (!state.type) { state.type = "physical" }
    state.remove("delayEndTime")
	def sk = device.currentValue("securityKeypad")
    if(sk != "armed away") {
        keypadUpdateStatus(0x0B, state.type, state.code)

        Date now = new Date()
        long ems = now.getTime()
        sendEvent(name:"alarmStatusChangeTime", value: "${now}", isStateChange:true)
        sendEvent(name:"alarmStatusChangeEpochms", value: "${ems}", isStateChange:true)
        changeStatus("set")
    }
}

void armHome(delay=state?.keypadConfig?.armHomeDelay) { //MikeNYC made delay default to state.keypadConfig if null or not provided
    trace("In Method : armHome(${delay})")
	switch (forceStateChangeThru){
		case "HSM" :
			runInMillis (1, "checkAgainstHSM",[data:[command : "armHome", delay : delay], misfire: "ignore"])
			break
		case "Mode" :
			runInMillis (1, "checkAgainstMode",[data:[command : "armHome", delay : delay], misfire: "ignore"])
			break
		case "App/Rule" :
			runInMillis (1, "confirmAppDriven",[data:[command : "armHome", delay : delay], misfire: "ignore"])
			break
		default :
			eventProcess(name:"armingIn", value: "armHome", data:[armCmd: "armHome"])//, isStateChange:true) HSM won't trigger if it isn't a change of state. HSM can trigger this. We can't have looping logic.
			runInMillis (1, "armHomeStart",[data: delay, misfire: "ignore"]) //delay gives securityKeypad an opportunity to update & block a subsequent method calling
			break
	}
}

void armHomeStart(delay=state?.keypadConfig?.armHomeDelay) { //MikeNYC made delay default to state.keypadConfig if null or not provided
    trace("In Method : armHomeStart(${delay})")
    def sk = device.currentValue("securityKeypad")
    if(sk != "armed home" && sk != "arming home") { //MikeNYC added the 2nd arming state to ensure it can't go through the loop again by mistake
		eventProcess(name: "securityKeypad", value: "arming home") //MikeNYC added this to ensure if the HSM finishes 1st and resends the call for this method, that it isn't looped through again.
		if (delay > 0) {
			exitDelay(delay)
            runIn(delay, armHomeEnd, [misfire: "ignore"])
		}else{
            armHomeEnd()
        }
    } else {
        debug("In armHome - securityKeypad already set to 'armed home' or 'arming home', so skipping.")
    }
}

void armHomeEnd() {
	trace("In Method : armHomeEnd()")
    if (!state.code) { state.code = "" }
    if (!state.type) { state.type = "physical" }
    state.remove("delayEndTime")
	def sk = device.currentValue("securityKeypad")
    if(sk != "armed home") {
        keypadUpdateStatus(0x0A, state.type, state.code)

        Date now = new Date()
        long ems = now.getTime()
        sendEvent(name:"alarmStatusChangeTime", value: "${now}", isStateChange:true)
        sendEvent(name:"alarmStatusChangeEpochms", value: "${ems}", isStateChange:true)
        changeStatus("set")
    }
}

void disarm(delay) {
    trace("In Method : disarm(${delay})")
	switch (forceStateChangeThru){
		case "HSM" :
			runInMillis (1, "checkAgainstHSM",[data:[command : "disarm", delay : delay], misfire: "ignore"])
			break
		case "Mode" :
			runInMillis (1, "checkAgainstMode",[data:[command : "disarm", delay : delay], misfire: "ignore"])
			break
		case "App/Rule" :
			runInMillis (1, "confirmAppDriven",[data:[command : "disarm", delay : delay], misfire: "ignore"])
			break
		default :
			if (disarmAll == true) {
			eventProcess(name:"armingIn", value:"disarmAll", data:[armCmd: "disarmAll"])//, isStateChange:true) HSM won't trigger if it isn't a change of state. HSM can trigger this. We can't have looping logic.
			} else {
				eventProcess(name:"armingIn", value: "disarm", data:[armCmd: "disarm"])//, isStateChange:true) HSM won't trigger if it isn't a change of state. HSM can trigger this. We can't have looping logic.
			}
			runInMillis (1, "disarmStart",[data: delay, misfire: "ignore"]) //delay gives securityKeypad an opportunity to update & block a subsequent method calling
			break
	}
}

void disarmStart(delay) {
	trace("In Method : disarmStart(${delay})")
    def sk = device.currentValue("securityKeypad")
    if(sk != "disarmed" && sk != "disarming") {
		if (sk != "re-arm during partial") {
			eventProcess(name: "securityKeypad", value: "disarming")
		}
		disarmEnd()
    } else {
        debug("In disarm - securityKeypad already set to 'disarmed', so skipping.")
    }
}

void disarmEnd() {
	trace("In Method : disarmEnd()")
    if (!state.code) { state.code = "" }
    if (!state.type) { state.type = "physical" }
    state.remove("delayEndTime")
	def sk = device.currentValue("securityKeypad")
    if(sk != "disarmed") {
        if (sk != "re-arm during partial"){ // MikeNYC88 - No need to have the keypad announce it's disarmed or have events/status changes 
            keypadUpdateStatus(0x02, state.type, state.code)

            Date now = new Date()
            long ems = now.getTime()
			sendEvent(name:"alarmStatusChangeTime", value: "${now}", isStateChange:true)
            sendEvent(name:"alarmStatusChangeEpochms", value: "${ems}", isStateChange:true)

            changeStatus("off")
        }
        unschedule(armHomeEnd)
        unschedule(armAwayEnd)
        unschedule(armNightEnd)
        unschedule(changeStatus)
    } else {
        debug("In disarm - securityKeypad already set to 'disarmed', so skipping.")
    }
}

// Methods - Keypad Control - Delays
void setExitDelay(Map delays){
	trace("In Method : setExitDelay(${delays})")
	if (delays) {
		vArmNightDelay = (delays?.nightDelay ?: 0).toInteger()
		vArmHomeDelay = (delays?.homeDelay ?: 0).toInteger()
		vArmAwayDelay = (delays?.awayDelay ?: 0).toInteger()
	} else {
		vArmNightDelay = armNightDelay
		vArmHomeDelay = armHomeDelay
		vArmAwayDelay = armAwayDelay
	}
	if (logEnableForSetExitDelays) {info("In setExitDelay (${version()}) - delay: [Away: ${armAwayDelay}, Night: ${vArmNightDelay}, Home :${armHomeDelay}]")}
	state.keypadConfig.armNightDelay = (vArmNightDelay ?: 0).toInteger()
	state.keypadConfig.armHomeDelay = (vArmHomeDelay ?: 0).toInteger()
	state.keypadConfig.armAwayDelay = (vArmAwayDelay ?: 0).toInteger()
	device.updateSetting("armNightDelay", [value: vArmNightDelay, type: "number"])//in case Delays map was provided
	device.updateSetting("armHomeDelay", [value: vArmHomeDelay, type: "number"])//in case Delays map was provided
	device.updateSetting("armAwayDelay", [value: vArmAwayDelay, type: "number"])//in case Delays map was provided
}

void exitDelay(){
	trace("In Method : exitDelay()")
    int intDelay = state.keypadConfig.exitDelay ? state.keypadConfig.exitDelay.toInteger() : 0
    if (intDelay) exitDelay(intDelay)
}

void exitDelay(delay){
	trace("In Method : exitDelay(${delay})")
    if (delay && delay > 0) {
        state.keypadStatus = "18"
        def now = new Date()
		state.delayEndTime = now.getTime() + (delay*1000)
		state.keypadConfig.exitDelay = delay != null ? delay.toInteger() : 0 //MikeNYC88 added
		sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x12, propertyId:7, value:delay.toInteger()]]).format())
    }
}

void entry(){
	trace("In Method : entry()")
    int intDelay = state.keypadConfig.entryDelay ? state.keypadConfig.entryDelay.toInteger() : 0
    if (intDelay) entry(intDelay)
}

void entry(entranceDelay){
	trace("In Method : entry(${entranceDelay})")
    if (entranceDelay && entranceDelay > 0) {
		state.keypadStatus = "17" // MikeNYC88 added
		def now = new Date()
		state.delayEndTime = now.getTime() + (entranceDelay*1000)
		state.keypadConfig.entryDelay = entranceDelay != null ? entranceDelay.toInteger() : 0
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x11, propertyId:7, value:entranceDelay.toInteger()]]).format())
    }
}

// Methods - Commands - alarm capability

def stop() { // MikeNYC88 - Added to allow the stop command to work in details screen
	trace("In Method : stop()")
    off()
}

void off() {
	trace("In Method : off()")
    eventProcess(name:"alarm", value:"off")
    changeStatus("off")
	if (state.delayEndTime) {
		def now = new Date()	
		def delayLeft = Math.floor((state.delayEndTime - now.getTime())/1000)//delay left, rounded down to nearest integer
		debug("delay left : ${delayLeft}")
		switch (state.keypadStatus){
			case "18": //This is in the case that it is mid-exit delay
				sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x12, propertyId:7, value:delayLeft.toInteger()]]).format())
			break
			case "17": //This is in the case that it is mid-entry delay
			sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x11, propertyId:7, value:delayLeft.toInteger()]]).format())
			break
			default :
				sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:state.keypadStatus, propertyId:2, value:0xFF]]).format())
			break
		}
	} else {
		sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:state.keypadStatus, propertyId:2, value:0xFF]]).format())
	}
}

void both() {
	trace("In Method : both()")
    siren()
}

void siren() {
	trace("In Method : siren()")
    eventProcess(name:"alarm", value:"siren")
    changeStatus("siren")
    sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x0C, propertyId:2, value:0xFF]]).format())
}

void strobe() {
	trace("In Method : strobe()")
    eventProcess(name:"alarm", value:"strobe")
    changeStatus("strobe")
	sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x13, propertyId:2, value:0xFF]]).format()) // MikeNYC88 - closest thing to a strong w/ no siren is the "medical" alert (IndicatorID 0x13)
}

//Methods - Preferences & Misc. Commands
def constructMakerApiHsmUrl(note="not used",ipAddress,appID,accessToken){
	trace("In Method : constructMakerApiHsmUrl('not used',${ipAddress},${appID},${accessToken})")
	constructedUrl = "https://${ipAddress}/apps/api/${appID}/hsm?access_token=${accessToken}" //use HTTPS & then ignore SSL issues in case it is HTTP
	device.updateSetting("makerApiHsmUrl", [value: constructedUrl, type: "string"])
	info("Set preference 'makerApiHsmUrl'")//not shown in logs for security purposes
}

def pullExternalHubHsmStatus(){
	trace("In Method : pullExternalHubHsmStatus()")
	def hsmStatus = null
	def httpParams = 
		[
			uri: "${makerApiHsmUrl}",
			ignoreSSLIssues: true
		]
	try {
		httpGet(httpParams) { response ->
			if (response.status == 200) {
				hsmStatus = response.data.hsm
				debug("HSM Status from external hub: ${hsmStatus}")
			} else {
				error("Failed to get HSM status from external hub: ${response.status}")
			}
		}
	} catch (Exception e) {
		error("Error occurred while getting HSM status: ${e.message}")
	}
	return hsmStatus
}

void setPartialFunction(mode = null) {
	trace("In Method : setPartialFunction(${mode})")
    if (!mode) {
        mode = partialFunctionValue
    }
    if ( !(mode in ["armHome","armNight"]) ) {
        warn("Unable set partialFunction button. Custom command used by HSM.")
    } else if (mode in ["armHome","armNight"]) {
        state.keypadConfig.partialFunction = mode == "armHome" ? "armHome" : "armNight"
    }
}

void proximitySensorHandler(onInstall) { //MikeNYC88 added the onInstall to ensure that it is disabled on Install
	trace("In Method : proximitySensorHandler(${onInstall})")
    if(disableProximitySensor || onInstall == true) { //MikeNYC88 added the onInstall to ensure that it is disabled on Install
        info("Turning the Proximity Sensor OFF")
        sendToDevice(new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber: 15, size: 1, scaledConfigurationValue: 0).format())
    } else {
        info("Turning the Proximity Sensor ON")
        sendToDevice(new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber: 15, size: 1, scaledConfigurationValue: 1).format())
    }
}

def setVolumeSettings() {
	trace("In Method : setVolumeSettings()")
	//Annoucement section
	newAnnouncementVol = volAnnouncement // MikeNYC88 added
    debug("In volAnnouncement (${version()}) - newVol: ${newAnnouncementVol}")
	if(newAnnouncementVol) {
        def currentAnnouncementVol = device.currentValue('volAnnouncement')
        if (newAnnouncementVol>10) { //MikeNYC88 added to ensure not less than 0 or greater than 10
			newAnnouncementVol=10
			device.updateSetting("volAnnouncement", [value: 10, type: "number"]) // MikeNYC88 added
            info("volAnnouncement must be a integer between 0 and 10 (no less/more). Making volAnnouncement = 10")
        }
        if(newAnnouncementVol.toString() == currentAnnouncementVol.toString()) {
            info("Announcement Volume hasn't changed, so skipping")
        } else {
            //device.updateSetting("volAnnouncement", [value: newAnnouncementVol.toInteger(), type: "number"]) // MikeNYC88 added
            nAnnouncementVol = newAnnouncementVol.toInteger()
            sendToDevice(new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: nAnnouncementVol).format())
            eventProcess(name:"volAnnouncement", value: newAnnouncementVol, isStateChange:true)
        }
    } else {
        info("Announcement value not specified, so skipping")
    }
	
    //Keytone section	
	newKeytoneVol = volKeytone // MikeNYC88 added
    debug("In volKeytone (${version()}) - newVol: ${newKeytoneVol}")
    if(newKeytoneVol) {
        def currentKeytoneVol = device.currentValue('volKeytone')
        if (newKeytoneVol>10) { //MikeNYC88 added to ensure not less than 0 or greater than 10
			newKeytoneVol=10
			device.updateSetting("volKeytone", [value: 10, type: "number"]) // MikeNYC88 added
            info("volKeytone must be a integer between 0 and 10 (no less/more). Making volKeytone = 10")
        }
        if(newKeytoneVol.toString() == currentKeytoneVol.toString()) {
            info("Keytone Volume hasn't changed, so skipping")
        } else {
            //device.updateSetting("volKeytone", [value: newKeytoneVol.toInteger(), type: "number"]) // MikeNYC88 added
            nVolKeytone = newKeytoneVol.toInteger()
            sendToDevice(new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: nVolKeytone).format())
            eventProcess(name:"volKeytone", value: newKeytoneVol, isStateChange:true)
        }
    } else {
       info("Keytone value not specified, so skipping")
    }

	//Siren section
	newSirenVol = volSiren // MikeNYC88 added
    debug("In volSiren (${version()}) - newVol: ${newSirenVol}")
    if(newSirenVol) {
        def currentSirenVol = device.currentValue('volSiren')
        if (newSirenVol>10) { //MikeNYC88 added to ensure not less than 0 or greater than 10
			newSirenVol=10
			device.updateSetting("volSiren", [value: 10, type: "number"]) // MikeNYC88 added
            info("volSiren must be a integer between 0 and 10 (no less/more). Making volSiren = 10")
        }
        if(newSirenVol.toString() == currentSirenVol.toString()) {
            info("Siren Volume hasn't changed, so skipping")
		} else {
            //device.updateSetting("volSiren", [value: newSirenVol.toInteger(), type: "number"]) // MikeNYC88 added
            nSirenVol = newSirenVol.toInteger()
            sendToDevice(new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: nSirenVol).format()) //sVol).format()) //MikeNYC88 changed to nVol like the other volume settings
            eventProcess(name:"volSiren", value: newSirenVol, isStateChange:true)
        }
    } else {
        info("Siren value not specified, so skipping")
    }
}

def playSound (soundnumber) {
	trace("In Method : playSound(${soundnumber})")
	if(!soundnumber) {
        soundnumber = theTone
        debug("In playSound - Tone is NULL, so setting tone to theTone: ${theTone}")
    }
	if (soundnumber>11 || soundnumber<1) { soundnumber = 11 }
	sVol = device.currentValue('volSiren').toInteger()*10 //MikeNYC88 added... tones need to be out of 100, normal volume is out of 10
    debug("In playSound (${version()}) - tone: Chime ${soundnumber} at Volume: ${sVol}")
	switch (soundnumber){
    case 1:
        info("In playTone - Tone 1")    // Siren
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x0C, propertyId:2, value:sVol]]).format()) //propertyID 2 uses whatever is shown in the volSiren, from 1-10
	break
    case 2:
        info("In playTone - Tone 2")    // Siren + badge lit
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x0D, propertyId:2, value:sVol]]).format()) //propertyID 2 uses whatever is shown in the volSiren, from 1-10
	break
	case 3:
        info("In playTone - Tone 3")    // 3 chirps
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x0E, propertyId:2, value:sVol]]).format()) //propertyID 2 uses whatever is shown in the volSiren, from 1-10
	break
    case 4:
        info("In playTone - Tone 4")    // 4 chirps
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x0F, propertyId:2, value:sVol]]).format()) //propertyID 2 uses whatever is shown in the volSiren, from 1-10
    break
    case 5:
        info("In playTone - Tone 5")    // Navi
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x60, propertyId:0x09, value:sVol]]).format()) //propertyID 9 uses value given, from 0-100
    break
    case 6:
        info("In playTone - Tone 6")    // Guitar
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x61, propertyId:0x09, value:sVol]]).format())  //propertyID 9 uses value given, from 0-100
    break
    case 7:
        info("In playTone - Tone 7")    // Windchimes
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x62, propertyId:0x09, value:sVol]]).format())  //propertyID 9 uses value given, from 0-100
    break
    case 8:
        info("In playTone - Tone 8")    // Doorbell 1
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x63, propertyId:0x09, value:sVol]]).format())  //propertyID 9 uses value given, from 0-100
    break
    case 9:
        info("In playTone - Tone 9")    // Doorbell 2
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x64, propertyId:0x09, value:sVol]]).format())  //propertyID 9 uses value given, from 0-100
    break
	case 10:
		info("In playTone - Tone 10")    // Bypass Sound
		changeStatus("active")
		sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x10, propertyId:0x09, value:sVol]]).format())
    break
    case 11:
        info("In playTone - Tone 11")    // Invalid Code Sound
        changeStatus("active")
        sendToDevice(zwave.indicatorV3.indicatorSet(indicatorCount:1, value: 0, indicatorValues:[[indicatorId:0x09, propertyId:0x01, value:sVol]]).format())
    break
	default :
        info("In playTone - error... unknown which tone to play. Input a number 1 thru 10")    // test
        changeStatus("active")
    break
	}
}

void push(btn) {
	trace("In Method : push(${btn})")
    state.type = "digital"
    eventProcess(name: "pushed", value: btn, isStateChange: true)
}

void hold(btn) {
	trace("In Method : hold(${btn})")
    state.type = "digital"
    eventProcess(name: "held", value: btn, isStateChange:true)
    switch (btn) {
        case 11:
            Date now = new Date()
            long ems = now.getTime()
            eventProcess(name:"lastCodeName", value: "police", isStateChange:true)
            sendEvent(name:"lastCodeTime", value: "${now}", isStateChange:true)
            sendEvent(name:"lastCodeEpochms", value: "${ems}", isStateChange:true)
            break
        case 12:
            Date now = new Date()
            long ems = now.getTime()
            eventProcess(name:"lastCodeName", value: "fire", isStateChange:true)
            sendEvent(name:"lastCodeTime", value: "${now}", isStateChange:true)
            sendEvent(name:"lastCodeEpochms", value: "${ems}", isStateChange:true)
            break
        case 13:
            Date now = new Date()
            long ems = now.getTime()
            eventProcess(name:"lastCodeName", value: "medical", isStateChange:true)
            sendEvent(name:"lastCodeTime", value: "${now}", isStateChange:true)
            sendEvent(name:"lastCodeEpochms", value: "${ems}", isStateChange:true)
            break
    }
}

// Methods - Codes/PINs related
void setCodeLength(pincodelength) {
	trace("In Method : setCodeLength(${pincodelength})")
    eventProcess(name:"codeLength", value: pincodelength, descriptionText: "${device.displayName} codeLength set to ${pincodelength}")
    state.keypadConfig.codeLength = pincodelength
    // set zwave entry code key buffer
    // 6F06XX10
    sendToDevice("6F06" + hubitat.helper.HexUtils.integerToHexString(pincodelength.toInteger()+1,1).padLeft(2,'0') + "0F")
}

void getCodes(){
	trace("In Method : getCodes()")
    updateEncryption()
}

private updateEncryption(){
	trace("In Method : updateEncryption()")
    String lockCodes = device.currentValue("lockCodes") //encrypted or decrypted
    if (lockCodes){
        if (optEncrypt && lockCodes[0] == "{") { //resend encrypted
            sendEvent(name:"lockCodes",value: encrypt(lockCodes), isStateChange:true)
			info("lockCodes Encrypted")
        } else if (!optEncrypt && lockCodes[0] != "{") { //resend decrypted
			if(removeCodeFromLogs == false) {
				eventProcess(name:"lockCodes", value: decrypt(lockCodes), isStateChange:true)
			} else {
				sendEvent(name:"lockCodes", value: decrypt(lockCodes), isStateChange:true)
				info("lockCodes Decrypted")
			}
        } else {
            if(removeCodeFromLogs == false) {
				eventProcess(name:"lockCodes", value: lockCodes, isStateChange:true)
			} else {
				sendEvent(name:"lockCodes", value: lockCodes, isStateChange:true)
				info("lockCodes are not encrypted")
			}
        }
    }
}

private Boolean validatePin(String pincode) {
	trace("In Method : validatePin(${if (removeCodeFromLogs == false) {pincode} else{"..."}})")
    boolean retVal = false
    Map lockcodes = [:]
    if (optEncrypt) {
        try {
            lockcodes = parseJson(decrypt(device.currentValue("lockCodes")))
        } catch(e) {
            warn("Ring Alarm Keypad G2 Community - No lock codes found.")
        }
    } else {
        try {
            lockcodes = parseJson(device.currentValue("lockCodes"))
        } catch(e) {
            warn("Ring Alarm Keypad G2 Community - No lock codes found.")
        }
    }
	if (removeCodeFromLogs == false) {debug("Lock codes: ${lockcodes}")}
    if(lockcodes) {
        lockcodes.each {
            if(it.value["code"] == pincode) {
                Date now = new Date()
                long ems = now.getTime()
                if (removeCodeFromLogs == false) {info("found code: ${pincode} user: ${it.value['name']}")}
                eventProcess(name:"validCode", value: "true", isStateChange: true)
                eventProcess(name:"lastCodeName", value: "${it.value['name']}", isStateChange:true)
                sendEvent(name:"lastCodeTime", value: "${now}", isStateChange:true)
                sendEvent(name:"lastCodeEpochms", value: "${ems}", isStateChange:true)
                retVal=true
                if (removeCodeFromLogs == true) {
					state.code = "hidden"
				} else {
					String code = JsonOutput.toJson(["${it.key}":["name": "${it.value.name}", "code": "${it.value.code}", "isInitiator": true]])
					if (optEncrypt) {
						state.code=encrypt(code)
					} else {
						state.code=code
					}
				}
            }
        }
    }
	storeLastCode(pincode)//MikeNYC88 added this so that PINs/Codes used will be stored in state. "state.code" might be encrypted, this circumvents that. This allows the keypad to trigger rules outside of actual arming/disarming.
    if (!retVal) {
        eventProcess(name:"validCode", value: "false", isStateChange: true)
		eventProcess(name:"lastCodeName", value: "${if(removeCodeFromLogs == false) {pincode} else {"invalid"}}", isStateChange:true)
		sendEvent(name:"lastCodeTime", value: "${now}", isStateChange:true)
		sendEvent(name:"lastCodeEpochms", value: "${ems}", isStateChange:true)
    }
    return retVal
}

def storeLastCode(pincode){//MikeNYC88 added this so that PINs/Codes used will be stored in state. This allows the keypad to trigger rules outside of actual arming/disarming.
	trace("In Method : storeLastCode(${if (removeCodeFromLogs == false) {pincode} else{"..."}})")
	if (saveLastCode == true) {
		sendEvent(name:"lastCode", value: "${pincode}", isStateChange: true)
		runIn(30, "deleteLastCode", [misfire: "ignore"])
	} else {
		deleteLastCode()
	}
}

def deleteLastCode(){//MikeNYC88 added this so that PINs/Codes aren't stored indefinitely, which would be a security risk
	trace("In Method : deleteLastCode()")
	device.deleteCurrentState("lastCode")
}

void setCode(codeposition, pincode, name) {
	trace("In Method : setCode(${codeposition}, ${if (removeCodeFromLogs == false) {pincode} else {"..."}}, ${name})")
    boolean newCode = true
    Map lockcodes = [:]
    if (device.currentValue("lockCodes") != null) {
        if (optEncrypt) {
            lockcodes = parseJson(decrypt(device.currentValue("lockCodes")))
        } else {
            lockcodes = parseJson(device.currentValue("lockCodes"))
        }
    }
    if (lockcodes["${codeposition}"]) { newCode = false }
    lockcodes["${codeposition}"] = ["code": "${pincode}", "name": "${name}"]
    if (optEncrypt) {
        sendEvent(name: "lockCodes", value: encrypt(JsonOutput.toJson(lockcodes)))
    } else {
        sendEvent(name: "lockCodes", value: JsonOutput.toJson(lockcodes), isStateChange: true)
    }
    if (newCode) {
        sendEvent(name: "codeChanged", value:"added")
    } else {
        sendEvent(name: "codeChanged", value: "changed")
    }
	if (removeCodeFromLogs == false) {info("Updated lock codes: ${lockcodes}")}
}

void deleteCode(codeposition) {
	trace("In Method : deleteCode(${codeposition})")
    Map lockcodes=[:]
    if (device.currentValue("lockCodes") != null) {
        if (optEncrypt) {
            lockcodes = parseJson(decrypt(device.currentValue("lockCodes")))
        } else {
            lockcodes = parseJson(device.currentValue("lockCodes"))
        }
    }
    lockcodes["${codeposition}"] = [:]
    lockcodes.remove("${codeposition}")
    if (optEncrypt) {
        sendEvent(name: "lockCodes", value: encrypt(JsonOutput.toJson(lockcodes)))
    } else {
        sendEvent(name: "lockCodes", value: JsonOutput.toJson(lockcodes), isStateChange: true)
    }
    sendEvent(name: "codeChanged", value: "deleted")
	info("removed lock code position ${codeposition}")
	if (removeCodeFromLogs == false) {info("Updated lock codes: ${lockcodes}")}
}

//Methods - Send Keypad Commands
List<String> commands(List<String> cmds, Long delay=300) {
	trace("In Method : commands(List<String> cmds = ${cmds}, Long delay = ${delay})")
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

void sendToDevice(List<String> cmds, Long delay=300) {
	trace("In Method : sendToDevice(List<String> cmds = ${cmds}, Long delay = ${delay})")
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds, delay), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd, Long delay=300) {
	trace("In Method : sendToDevice(String cmd = ${cmd}, Long delay = ${delay})")
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

//Methods - Receive Keypad Commands, Parse, Act, and reply
void parse(String description) {
	trace("In Method : parse(${description})")
    ver = getDataValue("firmwareVersion")
    if(ver >= "1.18") {
        if(description.contains("6C01") && description.contains("FF 07 08 00")) {
            sendEvent(name:"motion", value: "active", isStateChange:true)
        } else if(description.contains("6C01") && description.contains("FF 07 00 01 08")) {
            sendEvent(name:"motion", value: "inactive", isStateChange:true)
        }
    }
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
	if (cmd) {
		debug("Object Class : ${getObjectClassName(cmd)}")
		debug("zwaveEvent cmd : ${cmd}")
        zwaveEvent(cmd)
    }
}

	// universal

void zwaveEvent(hubitat.zwave.Command cmd) {
	trace("In Method : zwaveEvent(hubitat.zwave.Command cmd)")
	error("Skipped command :${cmd}")
	error("Skipped Object Class : ${getObjectClassName(cmd)}")
}

void zwaveEvent(cmd) { // MikeNYC88 - Added this JUST IN CASE
	trace("In Method : zwaveEvent(cmd)")
	error("Skipped command :${cmd}")
	error("Skipped Object Class : ${getObjectClassName(cmd)}")
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation)")
	debug("cmd : ${cmd}")
	hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
	trace("In Method : zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet)")
	debug("cmd : ${cmd}")
    debug("Supervision Get - SessionID: ${cmd.sessionID}, CC: ${cmd.commandClassIdentifier}, Command: ${cmd.commandIdentifier}")
    if (cmd.commandClassIdentifier == 0x6F) {
        parseEntryControl(cmd.commandIdentifier, cmd.commandByte)
    } else {
        hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
        if (encapsulatedCommand) {
            zwaveEvent(encapsulatedCommand)
        }
    }
    // device quirk requires this to be unsecure reply
	debug("Sending supervisionReport reply to initial supervisionGet") 
    sendToDevice(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0).format())
}

	// Association V2

List<String> setDefaultAssociation() {
	trace("In Method : setDefaultAssociation()")
    List<String> cmds = []
    cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId).format())
    cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 1).format())
    return cmds
}

List<String> processAssociations(){
	trace("In Method : processAssociations()")
    List<String> cmds = []
    cmds.addAll(setDefaultAssociation())
    return cmds
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	trace("In Method : zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport)")
	debug("cmd : ${cmd}")
    List<String> temp = []
    if (cmd.nodeId != []) {
        cmd.nodeId.each {
            temp.add(it.toString().format( '%02x', it.toInteger() ).toUpperCase())
        }
    }
    debug("Association Report - Group: ${cmd.groupingIdentifier}, Nodes: $temp")
}

void zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorReport cmd) {
	trace("In Method : zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorReport)")
	Warn("Skipped indicatorv3.IndicatorReport cmd : ${cmd}")
    // Don't need to handle reports
}

	// standard config

List<String> runConfigs() {
	trace("In Method : runConfigs()")
    List<String> cmds = []
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.addAll(configCmd(param, data.parameterSize, settings[data.input.name]))
        }
    }
    return cmds
}

List<String> pollConfigs() {
	trace("In Method : pollConfigs()")
    List<String> cmds = []
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.add(zwave.configurationV1.configurationGet(parameterNumber: param.toInteger()).format())
        }
    }
    return cmds
}

List<String> configCmd(parameterNumber, size, scaledConfigurationValue) {
	trace("In Method : configCmd(${parameterNumber}, ${size}, ${scaledConfigurationValue})")
    List<String> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: scaledConfigurationValue.toInteger()).format())
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()).format())
    return cmds
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport)")
	debug("cmd : ${cmd}")
	if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam = configParams[cmd.parameterNumber.toInteger()]
        int scaledValue
        cmd.configurationValue.reverse().eachWithIndex { v, index ->
            scaledValue = scaledValue | v << (8 * index)
        }
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    }
}

	// Battery v1

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	trace("In Method : zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport)")
	debug("cmd : ${cmd}")
    Map evt = [name: "battery", unit: "%"]
    if (cmd.batteryLevel == 0xFF) {
        evt.descriptionText = "${device.displayName} has a low battery"
        evt.value = 1
    } else {
        evt.value = cmd.batteryLevel
        evt.descriptionText = "${device.displayName} battery is ${evt.value}${evt.unit}"
    }
    evt.isStateChange = true
	eventProcess(evt)
}

	// MSP V2

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport)")
	debug("cmd : ${cmd}")
	debug("Device Specific Report - DeviceIdType: ${cmd.deviceIdType}, DeviceIdFormat: ${cmd.deviceIdDataFormat}, Data: ${cmd.deviceIdData}")
    if (cmd.deviceIdType == 1) {
        String serialNumber = ""
        if (cmd.deviceIdDataFormat == 1) {
            cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff,1).padLeft(2, '0')}
        } else {
            cmd.deviceIdData.each { serialNumber += (char) it }
        }
        device.updateDataValue("serialNumber", serialNumber)
    }
}

	// Version V2

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport)")
	debug("cmd : ${cmd}")
	Double firmware0Version = cmd.firmware0Version + (cmd.firmware0SubVersion / 100)
    Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
    debug("Version Report - FirmwareVersion: ${firmware0Version}, ProtocolVersion: ${protocolVersion}, HardwareVersion: ${cmd.hardwareVersion}")
    device.updateDataValue("firmwareVersion", "${firmware0Version}")
    device.updateDataValue("protocolVersion", "${protocolVersion}")
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
    if (cmd.firmwareTargets > 0) {
        cmd.targetVersions.each { target ->
            Double targetVersion = target.version + (target.subVersion / 100)
            device.updateDataValue("FirmwareTarget${target.target}Version", "${targetVersion}")
        }
    }
}

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd) {
    trace("In Method : zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport)")
	debug("cmd : ${cmd}")
	Map evt = [:]
    if (cmd.notificationType == 8) {
        // power management
        switch (cmd.event) {
            case 1:
                // Power has been applied
                info("${device.displayName} Power has been applied")
                break
            case 2:
                // AC mains disconnected
                evt.name = "powerSource"
                evt.value = "battery"
                evt.descriptionText = "${device.displayName} AC mains disconnected"
				eventProcess(evt)
                break
            case 3:
                // AC mains re-connected
                evt.name = "powerSource"
                evt.value = "mains"
                evt.descriptionText = "${device.displayName} AC mains re-connected"
				eventProcess(evt)
                break
            case 12: //MikeNYC88 - I don't think this is even an option given the product documentation
                // battery is charging 
                info("${device.displayName} Battery is charging")
                break
			case 5:
                // brownout
                info("${device.displayName} experienced a 'brownout' ('Voltage Drop/Drift')")
                break
			default :
			debug("Not yet identified/coded Power Management Notification received from device : ${cmd.event}")
				break
        }
    }
	if (cmd.notificationType == 9) {
        // system
        if (cmd.event == 4) {
        	List<Short> payload = cmd.getPayload() 
			if (payload && payload.size() >= cmd.eventParametersLength) { 
				def stateParameterValue = payload[0] // Assuming first element is the state parameter value 
				switch (stateParameterValue) { 
					case 85 :
						error("Device gave error : System Software Failure")
					break
					case 170 :
						error("Device gave error : Soft Fault")
					break
					case 169 :
						error("Device gave error : SDK Value for Soft Fault")
					break
					case 172 :
						warn("Device gave warning : Software Reset (Not triggered by failure)")
					break
					case 171 :
						warn("Device gave warning : Pin Reset (soft reset)")
					break
				} 
			}       
        }
    }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	trace("In Method : zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport)")
	warn("skipped basicv1.BasicReport cmd : ${cmd}")
    // this is redundant/ambiguous and I don't care what happens here
}
