import datetime
import os
import json
import time

try:
    import speech_recognition as sr
except ImportError:
    sr = None

class JarvisAssistant:
    def __init__(self, user_name="Aamir Hussain"):
        self.user_name = user_name
        print(f"[*] Jarvis initialized for {self.user_name}")

    def speak(self, text):
        # Voice output / Speech synthesis simulation or Termux TTS fallback
        print(f"Jarvis: {text}")
        # Agar aap Termux par hain to tts-speak ka use kar sakte hain:
        # os.system(f"termux-tts-speak '{text}'")

    def get_time_and_date(self):
        now = datetime.datetime.now()
        current_time = now.strftime("%I:%M %p")
        return f"Yes Sir, अभी का समय यह रहा: {current_time}"

    def get_battery_status(self):
        try:
            battery_info = os.popen("termux-battery-status").read()
            if battery_info:
                data = json.loads(battery_info)
                percentage = data.get("percentage", "Unknown")
                return f"Aamir bhai, aapke phone ki battery {percentage}% hai."
        except Exception:
            pass
        return "Aamir bhai, battery status check ho raha hai."

    def listen_command(self):
        if not sr:
            print("Speech recognition library not installed.")
            return ""
        
        r = sr.Recognizer()
        with sr.Microphone() as source:
            print("\n[Listening for 'Jarvis'...] please speak...")
            r.adjust_for_ambient_noise(source, duration=1)
            try:
                audio = r.listen(source, timeout=5, phrase_time_limit=5)
                text = r.recognize_google(audio, language="en-IN")
                print(f"You said: {text}")
                return text.lower()
            except Exception:
                return ""

    def run_assistant(self):
        self.speak(f"Hello {self.user_name}, I am online and listening for your command.")
        while True:
            # Step 1: Wake word sunne ka intezaar karega (Jaise hi aap 'jarvis' bolenge tabhi activate hoga)
            command = self.listen_command()
            
            if "jarvis" in command:
                self.speak("Yes Sir, boliye kya hukm hai?")
                
                # Step 2: Agli command sunega
                sub_command = self.listen_command()
                
                if "time" in sub_command or "samay" in sub_command or "waqt" in sub_command:
                    response = self.get_time_and_date()
                    self.speak(response)
                elif "battery" in sub_command:
                    response = self.get_battery_status()
                    self.speak(response)
                elif "exit" in sub_command or "band" in sub_command:
                    self.speak("Theek hai Aamir bhai, main rest kar raha hoon.")
                    break
                else:
                    if sub_command:
                        self.speak(f"Aapne kaha: {sub_command}")
            
            time.sleep(1)

# Main Execution
if __name__ == "__main__":
    jarvis = JarvisAssistant(user_name="Aamir Hussain")
    
    # Agar aapko direct test karna hai bina mic ke:
    print(jarvis.get_time_and_date())
    
    # Live listening loop chalane ke liye niche ka line uncomment karein:
    # jarvis.run_assistant()
    
