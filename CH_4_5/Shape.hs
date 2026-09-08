module Shape where

class Shape a where
    area :: a -> Double
    describe :: a -> String