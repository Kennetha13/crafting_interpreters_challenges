module Circle where

import Shape

data Circle = Circle Double

instance Shape Circle where
    area (Circle radius) =
        pi * radius * radius

    describe (Circle radius) =
        "Circle with radius " ++ show radius