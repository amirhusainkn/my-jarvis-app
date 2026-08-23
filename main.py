    import os
import pyttsx3
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.clock import Clock

class JarvisUI(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisUI, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 20
        self.spacing = 20

        self.status_label = Label(
            text="Jarvis Parwaaz Ke Liye Taiyaar Hai...",
            font_size='22sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        self.speak_button = Button(
            text="Jarvis Se Guftagu Karein",
            font_size='20sp',
            size_hint=(1, 0.2),
            background_color=(0, 0.4, 0.8, 1)
        )
        self.speak_button.bind(on_press=self.on_button_click)
        self.add_widget(self.speak_button)

        self.init_tts()
        Clock.schedule_once(self.welcome_speech, 1)

    def init_tts(self):
        try:
            self.engine = pyttsx3.init()
            self.engine.setProperty('rate', 140)
            
            voices = self.engine.getProperty('voices')
            for voice in voices:
                if "male" in voice.name.lower() or "david" in voice.name.lower():
                    self.engine.setProperty('voice', voice.id)
                    break
        except Exception as e:
            print("TTS Init Error:", e)

    def speak(self, text):
        try:
            self.engine.say(text)
            self.engine.runAndWait()
        except Exception as e:
            print("Speaking Error:", e)

    def welcome_speech(self, dt):
        self.status_label.text = "Jarvis Aapki Khidmat Mein Haazir Hai..."
        self.speak("Aadaab Aamir Hussain bhai. Main Jarvis hoon... Aapki khidmat mein haazir hoon.")

    def on_button_click(self, instance):
        self.status_label.text = "Jarvis Sun Raha Hai..."
        self.speak("Ji Aamir bhai, hukum kijiye... Main aapki baat sun raha hoon.")

class JarvisApp(App):
    def build(self):
        return JarvisUI()

if __name__ == '__main__':
    JarvisApp().run()
    
