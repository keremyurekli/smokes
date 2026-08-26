# reactive smokes

A PaperMC plugin that adds reactive voxel smokes to Minecraft, inspired by Counter-Strike 2.<br>
It's written for PaperMC 26.2.<br>

You can get a smoke grenade using `/smokes get 3`.<br>
You can use any float as the power value. It controls the radius of the smoke; I think `3` looks the best.<br>

The grenade itself is a spinning brick made with a Block Display entity riding a slime.<br>
The slime is there because I needed something to handle the physics.<br>
The smoke itself is also made of many Block Display entities.<br>

AI was used minimally in the project
